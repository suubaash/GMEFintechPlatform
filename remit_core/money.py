"""Money value type (WS1-01): immutable, ISO-4217 minor units, currency-safe arithmetic."""
from __future__ import annotations
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from .errors import CurrencyMismatch, ValidationError

MINOR_UNITS = {
    "JPY": 0, "KRW": 0, "VND": 0, "CLP": 0, "ISK": 0,
    "BHD": 3, "KWD": 3, "OMR": 3, "TND": 3,
    "NPR": 2, "USD": 2, "EUR": 2, "GBP": 2, "INR": 2, "UZS": 2,
}


def minor_units(currency: str) -> int:
    """Decimal places for a currency (default 2)."""
    return MINOR_UNITS.get(currency.upper(), 2)


@dataclass(frozen=True)
class Money:
    amount_minor: int          # signed; negative allowed (ledger deltas)
    currency: str

    def __post_init__(self):
        if not isinstance(self.amount_minor, int) or isinstance(self.amount_minor, bool):
            raise ValidationError("amount_minor must be an int (minor units)")
        object.__setattr__(self, "currency", self.currency.upper())

    @classmethod
    def from_major(cls, amount, currency: str) -> "Money":
        dp = minor_units(currency)
        q = Decimal(str(amount)).quantize(Decimal(1).scaleb(-dp), rounding=ROUND_HALF_UP)
        return cls(int(q.scaleb(dp)), currency)

    def to_major(self) -> Decimal:
        dp = minor_units(self.currency)
        return Decimal(self.amount_minor) / (Decimal(10) ** dp)

    def _same(self, other: "Money"):
        if not isinstance(other, Money):
            raise ValidationError("operand must be Money")
        if self.currency != other.currency:
            raise CurrencyMismatch(f"currency mismatch: {self.currency} vs {other.currency}")

    def __add__(self, other): self._same(other); return Money(self.amount_minor + other.amount_minor, self.currency)
    def __sub__(self, other): self._same(other); return Money(self.amount_minor - other.amount_minor, self.currency)
    def __neg__(self):        return Money(-self.amount_minor, self.currency)

    def __mul__(self, k):
        if not isinstance(k, int) or isinstance(k, bool):
            raise ValidationError("Money can only be multiplied by an int")
        return Money(self.amount_minor * k, self.currency)

    def __lt__(self, o): self._same(o); return self.amount_minor < o.amount_minor
    def __le__(self, o): self._same(o); return self.amount_minor <= o.amount_minor
    def __gt__(self, o): self._same(o); return self.amount_minor > o.amount_minor
    def __ge__(self, o): self._same(o); return self.amount_minor >= o.amount_minor

    @property
    def is_zero(self) -> bool: return self.amount_minor == 0
    @property
    def is_negative(self) -> bool: return self.amount_minor < 0

    def __str__(self) -> str: return f"{self.to_major()} {self.currency}"
