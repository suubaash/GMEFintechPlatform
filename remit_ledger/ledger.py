"""Double-entry operational ledger (WS2). Append-only (engine-enforced), balanced JVs,
typed balances, reservations. Reference store: SQLite. Production: PostgreSQL (VIII.1)."""
from __future__ import annotations
import sqlite3
from datetime import date, datetime, timezone
from typing import Dict, List, Optional, Tuple

from remit_core import Money, BalanceType, EventEnvelope
from remit_core.errors import (LedgerUnbalanced, ValidationError, InsufficientBalance)
from .coa import ROLE_TYPE, normal_side

_SCHEMA = """
CREATE TABLE account(account_code TEXT PRIMARY KEY, currency TEXT NOT NULL,
                     account_type TEXT NOT NULL, account_role TEXT NOT NULL);
CREATE TABLE movement(movement_id INTEGER PRIMARY KEY AUTOINCREMENT, leg_id TEXT,
                      transfer_id TEXT, movement_type TEXT NOT NULL, currency TEXT NOT NULL,
                      amount_minor INTEGER NOT NULL, value_date TEXT);
CREATE TABLE journal_voucher(jv_id INTEGER PRIMARY KEY AUTOINCREMENT, movement_id INTEGER,
                             currency TEXT NOT NULL, posted_at TEXT NOT NULL);
CREATE TABLE posting(posting_id INTEGER PRIMARY KEY AUTOINCREMENT, jv_id INTEGER NOT NULL,
                     account_code TEXT NOT NULL, direction TEXT NOT NULL,
                     amount_minor INTEGER NOT NULL, currency TEXT NOT NULL,
                     leg_id TEXT, transfer_id TEXT, value_date TEXT, cleared INTEGER DEFAULT 0,
                     FOREIGN KEY(jv_id) REFERENCES journal_voucher(jv_id),
                     FOREIGN KEY(account_code) REFERENCES account(account_code));
CREATE TABLE reservation(reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                         account_code TEXT NOT NULL, currency TEXT NOT NULL,
                         amount_minor INTEGER NOT NULL, status TEXT NOT NULL, leg_id TEXT);
CREATE INDEX ix_posting_acct ON posting(account_code);
CREATE TRIGGER posting_no_update BEFORE UPDATE ON posting BEGIN SELECT RAISE(ABORT,'ledger-immutable'); END;
CREATE TRIGGER posting_no_delete BEFORE DELETE ON posting BEGIN SELECT RAISE(ABORT,'ledger-immutable'); END;
CREATE TRIGGER jv_no_update BEFORE UPDATE ON journal_voucher BEGIN SELECT RAISE(ABORT,'ledger-immutable'); END;
CREATE TRIGGER jv_no_delete BEFORE DELETE ON journal_voucher BEGIN SELECT RAISE(ABORT,'ledger-immutable'); END;
"""


class Ledger:
    def __init__(self, db_path: str = ":memory:", event_bus=None):
        self._conn = sqlite3.connect(db_path)
        self._conn.execute("PRAGMA foreign_keys=ON")
        self._conn.executescript(_SCHEMA)
        self._bus = event_bus

    def ensure_account(self, role: str, currency: str) -> str:
        if role not in ROLE_TYPE:
            raise ValidationError(f"unknown account role {role}")
        code = f"{role}-{currency.upper()}"
        if not self._conn.execute("SELECT 1 FROM account WHERE account_code=?", (code,)).fetchone():
            self._conn.execute("INSERT INTO account VALUES(?,?,?,?)",
                               (code, currency.upper(), ROLE_TYPE[role], role))
            self._conn.commit()
        return code

    def _acct_type(self, code: str) -> str:
        r = self._conn.execute("SELECT account_type FROM account WHERE account_code=?", (code,)).fetchone()
        if not r:
            raise ValidationError(f"account {code} does not exist")
        return r[0]

    def post_jv(self, movement_type: str, currency: str,
                postings: List[Tuple[str, str, int]],
                leg_id: Optional[str] = None, transfer_id: Optional[str] = None,
                value_date: Optional[date] = None, cleared: bool = False) -> int:
        currency = currency.upper()
        if not postings:
            raise ValidationError("a JV needs at least one posting")
        debit = credit = 0
        for code, direction, amt in postings:
            if amt <= 0:
                raise ValidationError("posting amount must be > 0")
            if direction not in ("DEBIT", "CREDIT"):
                raise ValidationError("direction must be DEBIT or CREDIT")
            self._acct_type(code)
            if code.rsplit("-", 1)[-1] != currency:
                raise ValidationError(f"account {code} currency != JV currency {currency}")
            debit += amt if direction == "DEBIT" else 0
            credit += amt if direction == "CREDIT" else 0
        if debit != credit:
            raise LedgerUnbalanced(f"unbalanced JV in {currency}: debit {debit} != credit {credit}")

        amount = debit
        vd = (value_date or date.today()).isoformat()
        now = datetime.now(timezone.utc).isoformat()
        with self._conn:
            mid = self._conn.execute(
                "INSERT INTO movement(leg_id,transfer_id,movement_type,currency,amount_minor,value_date)"
                " VALUES(?,?,?,?,?,?)", (leg_id, transfer_id, movement_type, currency, amount, vd)).lastrowid
            jv_id = self._conn.execute(
                "INSERT INTO journal_voucher(movement_id,currency,posted_at) VALUES(?,?,?)",
                (mid, currency, now)).lastrowid
            for code, direction, amt in postings:
                self._conn.execute(
                    "INSERT INTO posting(jv_id,account_code,direction,amount_minor,currency,"
                    "leg_id,transfer_id,value_date,cleared) VALUES(?,?,?,?,?,?,?,?,?)",
                    (jv_id, code, direction, amt, currency, leg_id, transfer_id, vd, int(cleared)))
        if self._bus:
            self._bus.publish(EventEnvelope(
                "ledger.jv_posted", "journal_voucher", str(jv_id),
                {"movement_type": movement_type, "currency": currency, "amount_minor": amount,
                 "leg_id": leg_id, "transfer_id": transfer_id}))
        return jv_id

    def _sums(self, code: str, cleared_only: bool = False):
        q = ("SELECT direction, COALESCE(SUM(amount_minor),0) FROM posting WHERE account_code=?"
             + (" AND cleared=1" if cleared_only else "") + " GROUP BY direction")
        d = c = 0
        for direction, total in self._conn.execute(q, (code,)):
            if direction == "DEBIT": d = total
            else: c = total
        return d, c

    def balance(self, code: str) -> Dict[BalanceType, Money]:
        ccy = code.rsplit("-", 1)[-1]
        ns = normal_side(self._acct_type(code))
        d, c = self._sums(code)
        ledger = (d - c) if ns == "DEBIT" else (c - d)
        dcl, ccl = self._sums(code, cleared_only=True)
        cleared = (dcl - ccl) if ns == "DEBIT" else (ccl - dcl)
        reserved = self._conn.execute(
            "SELECT COALESCE(SUM(amount_minor),0) FROM reservation WHERE account_code=? AND status='ACTIVE'",
            (code,)).fetchone()[0]
        return {BalanceType.LEDGER: Money(ledger, ccy), BalanceType.RESERVED: Money(reserved, ccy),
                BalanceType.AVAILABLE: Money(ledger - reserved, ccy), BalanceType.CLEARED: Money(cleared, ccy)}

    def reserve(self, code: str, amount_minor: int, leg_id: Optional[str] = None) -> int:
        ccy = code.rsplit("-", 1)[-1]
        if amount_minor <= 0:
            raise ValidationError("reserve amount must be > 0")
        avail = self.balance(code)[BalanceType.AVAILABLE].amount_minor
        if avail < amount_minor:
            raise InsufficientBalance(f"available {avail} < reserve {amount_minor} on {code}")
        rid = self._conn.execute(
            "INSERT INTO reservation(account_code,currency,amount_minor,status,leg_id) VALUES(?,?,?,'ACTIVE',?)",
            (code, ccy, amount_minor, leg_id)).lastrowid
        self._conn.commit()
        return rid

    def release(self, reservation_id: int):
        self._conn.execute("UPDATE reservation SET status='RELEASED' WHERE reservation_id=?", (reservation_id,))
        self._conn.commit()

    def statement(self, code: str) -> List[dict]:
        ns = normal_side(self._acct_type(code))
        rows, running = [], 0
        for pid, direction, amt, mt in self._conn.execute(
                "SELECT p.posting_id,p.direction,p.amount_minor,m.movement_type FROM posting p "
                "JOIN journal_voucher j ON p.jv_id=j.jv_id JOIN movement m ON j.movement_id=m.movement_id "
                "WHERE p.account_code=? ORDER BY p.posting_id", (code,)):
            running += amt if direction == ns else -amt
            rows.append({"posting_id": pid, "movement_type": mt, "direction": direction,
                         "amount_minor": amt, "running_balance": running})
        return rows

    def route_to_suspense(self, amount_minor: int, currency: str, reference: str) -> int:
        nostro = self.ensure_account("OPERATOR_NOSTRO", currency)
        susp = self.ensure_account("SUSPENSE_CLEARING", currency)
        return self.post_jv("PAYIN", currency,
                            [(nostro, "DEBIT", amount_minor), (susp, "CREDIT", amount_minor)],
                            transfer_id=f"unmatched:{reference}")
