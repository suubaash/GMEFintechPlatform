"""WS2 ledger tests + the KR->NP golden-path worked example (V.4.7)."""
import sqlite3
import unittest
from remit_core import BalanceType, InMemoryEventBus
from remit_core.errors import LedgerUnbalanced, ValidationError, InsufficientBalance
from remit_ledger import Ledger


class TestLedger(unittest.TestCase):
    def setUp(self):
        self.bus = InMemoryEventBus()
        self.lg = Ledger(event_bus=self.bus)
        self.nostro = self.lg.ensure_account("OPERATOR_NOSTRO", "KRW")
        self.wallet = self.lg.ensure_account("CUSTOMER_WALLET", "KRW")
        self.fee = self.lg.ensure_account("FEE_INCOME", "KRW")

    def _payin(self, amt=100000):
        return self.lg.post_jv("PAYIN", "KRW",
            [(self.nostro, "DEBIT", amt), (self.wallet, "CREDIT", amt)], transfer_id="T1")

    def test_seed_and_duplicate(self):                       # WS2-01
        again = self.lg.ensure_account("CUSTOMER_WALLET", "KRW")
        self.assertEqual(again, self.wallet)                 # idempotent, no duplicate

    def test_balanced_posts(self):                           # WS2-04
        jv = self._payin()
        self.assertIsInstance(jv, int)

    def test_unbalanced_rejected_nothing_written(self):      # WS2-05
        before = self.lg._conn.execute("SELECT COUNT(*) FROM posting").fetchone()[0]
        with self.assertRaises(LedgerUnbalanced):
            self.lg.post_jv("PAYIN", "KRW",
                [(self.nostro, "DEBIT", 100), (self.wallet, "CREDIT", 99)])
        after = self.lg._conn.execute("SELECT COUNT(*) FROM posting").fetchone()[0]
        self.assertEqual(before, after)

    def test_immutability(self):                             # WS2-06
        self._payin()
        with self.assertRaises(sqlite3.Error):
            self.lg._conn.execute("UPDATE posting SET amount_minor=1")
        with self.assertRaises(sqlite3.Error):
            self.lg._conn.execute("DELETE FROM posting")

    def test_typed_balances(self):                           # WS2-08
        self._payin()
        b = self.lg.balance(self.wallet)
        self.assertEqual(b[BalanceType.LEDGER].amount_minor, 100000)
        self.assertEqual(b[BalanceType.AVAILABLE].amount_minor, 100000)
        self.assertEqual(b[BalanceType.RESERVED].amount_minor, 0)

    def test_reserve_release(self):                          # WS2-09
        self._payin()
        rid = self.lg.reserve(self.wallet, 30000)
        self.assertEqual(self.lg.balance(self.wallet)[BalanceType.AVAILABLE].amount_minor, 70000)
        self.lg.release(rid)
        self.assertEqual(self.lg.balance(self.wallet)[BalanceType.AVAILABLE].amount_minor, 100000)

    def test_negative_prevention(self):                      # WS2-10
        self._payin(1000)
        with self.assertRaises(InsufficientBalance):
            self.lg.reserve(self.wallet, 5000)

    def test_reversal_nets(self):                            # WS2-07
        self._payin(1000)
        # reverse: debit wallet, credit nostro (offsetting)
        self.lg.post_jv("REVERSAL", "KRW",
            [(self.wallet, "DEBIT", 1000), (self.nostro, "CREDIT", 1000)], transfer_id="T1")
        self.assertEqual(self.lg.balance(self.wallet)[BalanceType.LEDGER].amount_minor, 0)
        self.assertEqual(self.lg.balance(self.nostro)[BalanceType.LEDGER].amount_minor, 0)

    def test_statement_running_balance(self):                # WS2-14
        self._payin(1000)
        self.lg.post_jv("FEE", "KRW",
            [(self.wallet, "DEBIT", 200), (self.fee, "CREDIT", 200)], transfer_id="T1")
        st = self.lg.statement(self.wallet)
        self.assertEqual([r["running_balance"] for r in st], [1000, 800])

    def test_gl_event_emitted(self):                         # WS2-12
        self._payin()
        kinds = [e.event_type for e in self.bus.delivered]
        self.assertIn("ledger.jv_posted", kinds)

    def test_suspense(self):                                 # WS2-15
        self.lg.route_to_suspense(5000, "KRW", "ref-1")
        susp = "SUSPENSE_CLEARING-KRW"
        # asset, credited -> negative natural balance (open item)
        self.assertEqual(self.lg.balance(susp)[BalanceType.LEDGER].amount_minor, -5000)


class TestGoldenPathKR_NP(unittest.TestCase):                # WS2-16 (V.4.7)
    def test_full_flow(self):
        lg = Ledger()
        A = lambda role, ccy: lg.ensure_account(role, ccy)
        # opening prefund float for NPR (VIII.12.3 opening balance)
        lg.post_jv("PREFUND_TOPUP", "NPR",
            [(A("PARTNER_PREFUND", "NPR"), "DEBIT", 5000000), (A("EQUITY", "NPR"), "CREDIT", 5000000)])
        # JV-1 payin 100,000 KRW
        lg.post_jv("PAYIN", "KRW",
            [(A("OPERATOR_NOSTRO","KRW"),"DEBIT",100000), (A("CUSTOMER_WALLET","KRW"),"CREDIT",100000)], transfer_id="TXN1")
        # JV-2 fee 5,000 KRW
        lg.post_jv("FEE", "KRW",
            [(A("CUSTOMER_WALLET","KRW"),"DEBIT",5000), (A("FEE_INCOME","KRW"),"CREDIT",5000)], transfer_id="TXN1")
        # JV-3 FX sell 95,000 KRW
        lg.post_jv("CONVERSION_SELL", "KRW",
            [(A("CUSTOMER_WALLET","KRW"),"DEBIT",95000), (A("SUSPENSE_CLEARING","KRW"),"CREDIT",95000)], transfer_id="TXN1")
        # JV-4 FX buy NPR: suspense 9,224.50 -> wallet 9,206.05 + FX_PNL 18.45
        lg.post_jv("CONVERSION_BUY", "NPR",
            [(A("SUSPENSE_CLEARING","NPR"),"DEBIT",922450),
             (A("CUSTOMER_WALLET","NPR"),"CREDIT",920605), (A("FX_PNL","NPR"),"CREDIT",1845)], transfer_id="TXN1")
        # JV-5 payout 9,206.05 NPR from prefund
        lg.post_jv("PAYOUT", "NPR",
            [(A("CUSTOMER_WALLET","NPR"),"DEBIT",920605), (A("PARTNER_PREFUND","NPR"),"CREDIT",920605)], transfer_id="TXN1")
        # JV-6 payout cost 100 NPR
        lg.post_jv("PAYOUT", "NPR",
            [(A("PARTNER_PAYOUT_COST","NPR"),"DEBIT",10000), (A("PARTNER_PREFUND","NPR"),"CREDIT",10000)], transfer_id="TXN1")

        L = BalanceType.LEDGER
        self.assertEqual(lg.balance("CUSTOMER_WALLET-KRW")[L].amount_minor, 0)
        self.assertEqual(lg.balance("CUSTOMER_WALLET-NPR")[L].amount_minor, 0)
        self.assertEqual(lg.balance("FEE_INCOME-KRW")[L].amount_minor, 5000)       # revenue: fee
        self.assertEqual(lg.balance("FX_PNL-NPR")[L].amount_minor, 1845)           # revenue: FX margin
        self.assertEqual(lg.balance("PARTNER_PAYOUT_COST-NPR")[L].amount_minor, 10000)  # cost
        self.assertEqual(lg.balance("OPERATOR_NOSTRO-KRW")[L].amount_minor, 100000)
        self.assertEqual(lg.balance("PARTNER_PREFUND-NPR")[L].amount_minor, 5000000 - 930605)
        # net revenue check (in KRW terms): 5000 fee + 190 KRW-equiv margin handled at reporting layer
        # gross revenue components captured: fee 5000 KRW + FX_PNL 1845 NPR; cost 10000 NPR


if __name__ == "__main__":
    unittest.main(verbosity=2)
