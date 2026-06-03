"""Typed error model (WS0-14). Every error renders to {code, message, issues[]}."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from typing import List, Optional


@dataclass
class Issue:
    field: str
    code: str
    message: str = ""


class RemitError(Exception):
    code = "error"

    def __init__(self, message: str = "", issues: Optional[List[Issue]] = None):
        super().__init__(message or self.code)
        self.message = message or self.code
        self.issues = issues or []

    def to_dict(self) -> dict:
        return {"code": self.code, "message": self.message,
                "issues": [asdict(i) for i in self.issues]}


class ValidationError(RemitError):        code = "validation-error"
class CurrencyMismatch(ValidationError):  code = "currency-mismatch"
class AccountConditionError(RemitError):  code = "account-condition"
class InsufficientBalance(AccountConditionError): code = "insufficient-balance"
class LedgerError(RemitError):            code = "ledger-error"
class LedgerUnbalanced(LedgerError):      code = "ledger-unbalanced"
class LedgerImmutable(LedgerError):       code = "ledger-immutable"
class IllegalTransition(RemitError):      code = "illegal-transition"
