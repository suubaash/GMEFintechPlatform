"""
swift_mt.py — SWIFT FIN message construction for payouts (MT103 + MT202/MT202COV).

Scope & honesty:
  * This builds the FIN *message text* (Blocks 1-5) for MT103 (single customer credit
    transfer) and MT202 / MT202 COV (general financial-institution transfer / cover).
  * It covers the mandatory fields and the most commonly used optional fields, with
    SWIFT-style formatting (decimal comma amounts, YYMMDD value dates, 'x' charset,
    BIC length checks, 4*35x party blocks) and structural validation.
  * It is a *reference* implementation: it is NOT a substitute for full SWIFT Standards
    Release field/network validation rules (MVR/SR), BIC-directory verification, or
    SWIFTNet transmission. Output is ready to hand to a SWIFT gateway / service bureau.

Dependencies: Python standard library only.
"""
from __future__ import annotations

import re
import uuid
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from typing import List, Optional


# --------------------------------------------------------------------------- #
# Formatting helpers
# --------------------------------------------------------------------------- #

# ISO 4217 minor units for currencies we format here; default 2.
CURRENCY_DECIMALS = {
    "JPY": 0, "KRW": 0, "VND": 0, "CLP": 0, "ISK": 0,
    "BHD": 3, "KWD": 3, "OMR": 3, "TND": 3,
    "NPR": 2, "USD": 2, "EUR": 2, "GBP": 2, "INR": 2, "UZS": 2,
}

# SWIFT 'x' character set (FIN text). Newlines (CrLf) are handled separately.
_X_CHARSET = re.compile(r"^[A-Za-z0-9/\-?:().,'+ ]*$")
_BIC_RE = re.compile(r"^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$")  # 8 or 11


class SwiftFormatError(ValueError):
    """Raised when a value cannot be represented in a SWIFT field."""


def minor_to_decimal(amount_minor: int, currency: str) -> Decimal:
    dp = CURRENCY_DECIMALS.get(currency.upper(), 2)
    return (Decimal(amount_minor) / (Decimal(10) ** dp)).quantize(
        Decimal(1).scaleb(-dp), rounding=ROUND_HALF_UP
    )


def format_amount(amount: Decimal, currency: str) -> str:
    """SWIFT amount: digits with a mandatory decimal comma, no thousands separators.
    Currencies with 0 decimals still carry a trailing comma (e.g. KRW '100000,')."""
    dp = CURRENCY_DECIMALS.get(currency.upper(), 2)
    q = amount.quantize(Decimal(1).scaleb(-dp), rounding=ROUND_HALF_UP)
    sign, digits, exp = q.as_tuple()
    if sign:
        raise SwiftFormatError("amount must be non-negative")
    s = f"{q:.{dp}f}"
    intpart, _, frac = s.partition(".")
    out = intpart + "," + frac  # comma is the decimal separator; trailing comma if dp==0
    if len(out.replace(",", "")) > 15:
        raise SwiftFormatError("amount exceeds 15 digits")
    return out


def format_value_date(d: date) -> str:
    return d.strftime("%y%m%d")


def check_charset(text: str, field_name: str) -> str:
    for line in text.split("\n"):
        if not _X_CHARSET.match(line):
            bad = set(ch for ch in line if not _X_CHARSET.match(ch))
            raise SwiftFormatError(f"{field_name}: chars not in SWIFT x-set: {sorted(bad)}")
    return text


def check_bic(bic: str, field_name: str) -> str:
    b = bic.strip().upper()
    if not _BIC_RE.match(b):
        raise SwiftFormatError(f"{field_name}: invalid BIC '{bic}' (need 8 or 11 chars)")
    return b


def truncate_lines(lines: List[str], max_lines: int, width: int = 35) -> List[str]:
    """Coerce a set of free-text lines into n*width (SWIFT party/narrative blocks)."""
    out: List[str] = []
    for ln in lines:
        ln = (ln or "").strip()
        while ln and len(out) < max_lines:
            out.append(ln[:width])
            ln = ln[width:]
        if len(out) >= max_lines:
            break
    return out


def ref16(value: str) -> str:
    """Field 20/21 reference: <=16x, no leading/trailing '/', no '//'."""
    v = re.sub(r"[^A-Za-z0-9/\-?:().,'+ ]", "", value).strip("/")[:16]
    v = v.replace("//", "/")
    if not v:
        raise SwiftFormatError("reference (:20:/:21:) is empty after sanitisation")
    return v


# --------------------------------------------------------------------------- #
# Domain models (kept independent of the platform; the service maps onto these)
# --------------------------------------------------------------------------- #

@dataclass
class Customer:
    """An ordering or beneficiary customer (a person/business)."""
    name: str
    address_lines: List[str] = field(default_factory=list)
    account: Optional[str] = None      # IBAN or local account number (e.g. 0211939494)
    country: Optional[str] = None      # ISO-3166 alpha-2, used in address tail


@dataclass
class Institution:
    """A financial institution identified primarily by BIC (option A)."""
    bic: str
    name: Optional[str] = None
    account: Optional[str] = None


# --------------------------------------------------------------------------- #
# Header / trailer blocks
# --------------------------------------------------------------------------- #

def _lt_address(bic: str, branch: str = "XXX") -> str:
    """12-char logical-terminal address: 8-char BIC + LT code 'A' + 3-char branch."""
    b8 = check_bic(bic, "header-bic")[:8]
    return f"{b8}A{branch}"


def block1(sender_bic: str, session: str = "0000", seq: str = "000000") -> str:
    return "{1:F01" + _lt_address(sender_bic) + session + seq + "}"


def block2_input(msg_type: str, receiver_bic: str, priority: str = "N") -> str:
    return "{2:I" + msg_type + _lt_address(receiver_bic) + priority + "}"


def block3(uetr: str, bank_priority: Optional[str] = None) -> str:
    parts = ""
    if bank_priority:
        parts += "{113:" + bank_priority + "}"
    parts += "{121:" + uetr + "}"      # UETR — mandatory in the gpi era
    return "{3:" + parts + "}"


def block5() -> str:
    # CHK is computed by the SWIFT interface on transmission; placeholder here.
    return "{5:{CHK:000000000000}}"


# --------------------------------------------------------------------------- #
# Field renderers (Block 4 tags)
# --------------------------------------------------------------------------- #

def render_customer_50K(c: Customer) -> str:
    lines = []
    if c.account:
        lines.append("/" + c.account)
    body = truncate_lines([c.name] + list(c.address_lines), 4 - len(lines))
    block = "\n".join(lines + body)
    return ":50K:" + check_charset(block, ":50K:")


def render_beneficiary_59(c: Customer) -> str:
    lines = []
    if c.account:
        lines.append("/" + c.account)
    tail = list(c.address_lines)
    if c.country:
        tail = tail + [c.country]
    body = truncate_lines([c.name] + tail, 4 - len(lines))
    block = "\n".join(lines + body)
    return ":59:" + check_charset(block, ":59:")


def render_institution_A(tag: str, inst: Institution) -> str:
    out = ""
    if inst.account:
        out += "/" + inst.account + "\n"
    out += check_bic(inst.bic, tag)
    return f":{tag}:" + out


def render_narrative(tag: str, text: str, max_lines: int = 4) -> str:
    body = "\n".join(truncate_lines((text or "").split("\n"), max_lines))
    return f":{tag}:" + check_charset(body, f":{tag}:")


# --------------------------------------------------------------------------- #
# MT103 — Single Customer Credit Transfer
# --------------------------------------------------------------------------- #

@dataclass
class MT103:
    sender_bic: str
    receiver_bic: str
    sender_reference: str                 # :20:
    value_date: date                      # :32A:
    currency: str
    amount: Decimal
    ordering_customer: Customer           # :50K:
    beneficiary_customer: Customer        # :59:
    bank_operation_code: str = "CRED"     # :23B:
    details_of_charges: str = "SHA"       # :71A: OUR/SHA/BEN
    instructed_currency: Optional[str] = None   # :33B:
    instructed_amount: Optional[Decimal] = None
    ordering_institution: Optional[Institution] = None        # :52A:
    senders_correspondent: Optional[Institution] = None       # :53A:
    intermediary_institution: Optional[Institution] = None    # :56A:
    account_with_institution: Optional[Institution] = None     # :57A:
    remittance_info: Optional[str] = None                     # :70:
    sender_to_receiver_info: Optional[str] = None             # :72:
    regulatory_reporting: Optional[str] = None                # :77B:
    uetr: str = field(default_factory=lambda: str(uuid.uuid4()))

    MSG_TYPE = "103"

    def validate(self) -> List[str]:
        errs: List[str] = []
        if self.details_of_charges not in ("OUR", "SHA", "BEN"):
            errs.append(":71A: must be OUR/SHA/BEN")
        if self.bank_operation_code not in ("CRED", "SPRI", "SSTD", "SPAY"):
            errs.append(":23B: invalid bank operation code")
        if self.amount <= 0:
            errs.append(":32A: amount must be > 0")
        try:
            check_bic(self.sender_bic, "sender")
            check_bic(self.receiver_bic, "receiver")
        except SwiftFormatError as e:
            errs.append(str(e))
        if not self.beneficiary_customer.name:
            errs.append(":59: beneficiary name required")
        return errs

    def block4(self) -> str:
        errs = self.validate()
        if errs:
            raise SwiftFormatError("MT103 invalid: " + "; ".join(errs))
        L = ["{4:"]
        L.append(":20:" + ref16(self.sender_reference))
        L.append(":23B:" + self.bank_operation_code)
        L.append(":32A:" + format_value_date(self.value_date)
                 + self.currency.upper() + format_amount(self.amount, self.currency))
        if self.instructed_amount is not None and self.instructed_currency:
            L.append(":33B:" + self.instructed_currency.upper()
                     + format_amount(self.instructed_amount, self.instructed_currency))
        L.append(render_customer_50K(self.ordering_customer))
        if self.ordering_institution:
            L.append(render_institution_A("52A", self.ordering_institution))
        if self.senders_correspondent:
            L.append(render_institution_A("53A", self.senders_correspondent))
        if self.intermediary_institution:
            L.append(render_institution_A("56A", self.intermediary_institution))
        if self.account_with_institution:
            L.append(render_institution_A("57A", self.account_with_institution))
        L.append(render_beneficiary_59(self.beneficiary_customer))
        if self.remittance_info:
            L.append(render_narrative("70", self.remittance_info))
        L.append(":71A:" + self.details_of_charges)
        if self.sender_to_receiver_info:
            L.append(render_narrative("72", self.sender_to_receiver_info, 6))
        if self.regulatory_reporting:
            L.append(render_narrative("77B", self.regulatory_reporting, 3))
        L.append("-}")
        return "\n".join(L)

    def build(self) -> str:
        return (block1(self.sender_bic)
                + block2_input(self.MSG_TYPE, self.receiver_bic)
                + block3(self.uetr)
                + "\n" + self.block4() + "\n"
                + block5())


# --------------------------------------------------------------------------- #
# MT202 / MT202 COV — General Financial Institution Transfer (+ cover)
# --------------------------------------------------------------------------- #

@dataclass
class MT202:
    sender_bic: str
    receiver_bic: str
    transaction_reference: str            # :20:
    related_reference: str                # :21: (the MT103 :20: for a cover, else NONREF)
    value_date: date
    currency: str
    amount: Decimal
    beneficiary_institution: Institution  # :58A:
    ordering_institution: Optional[Institution] = None    # :52A:
    senders_correspondent: Optional[Institution] = None   # :53A:
    receivers_correspondent: Optional[Institution] = None  # :54A:
    intermediary: Optional[Institution] = None            # :56A:
    account_with_institution: Optional[Institution] = None  # :57A:
    sender_to_receiver_info: Optional[str] = None         # :72:
    # COV sequence B (underlying customer credit transfer):
    cover: bool = False
    ordering_customer: Optional[Customer] = None          # seq B :50K:
    beneficiary_customer: Optional[Customer] = None        # seq B :59:
    cov_remittance_info: Optional[str] = None             # seq B :70:
    uetr: str = field(default_factory=lambda: str(uuid.uuid4()))

    @property
    def MSG_TYPE(self) -> str:
        return "202"

    def validate(self) -> List[str]:
        errs: List[str] = []
        if self.amount <= 0:
            errs.append(":32A: amount must be > 0")
        try:
            check_bic(self.sender_bic, "sender")
            check_bic(self.receiver_bic, "receiver")
            check_bic(self.beneficiary_institution.bic, ":58A:")
        except SwiftFormatError as e:
            errs.append(str(e))
        if self.cover and not (self.ordering_customer and self.beneficiary_customer):
            errs.append("COV requires sequence-B ordering & beneficiary customers")
        return errs

    def block4(self) -> str:
        errs = self.validate()
        if errs:
            raise SwiftFormatError("MT202 invalid: " + "; ".join(errs))
        L = ["{4:"]
        # Sequence A — the FI-to-FI transfer
        L.append(":20:" + ref16(self.transaction_reference))
        L.append(":21:" + (ref16(self.related_reference)
                           if self.related_reference.upper() != "NONREF" else "NONREF"))
        L.append(":32A:" + format_value_date(self.value_date)
                 + self.currency.upper() + format_amount(self.amount, self.currency))
        if self.ordering_institution:
            L.append(render_institution_A("52A", self.ordering_institution))
        if self.senders_correspondent:
            L.append(render_institution_A("53A", self.senders_correspondent))
        if self.receivers_correspondent:
            L.append(render_institution_A("54A", self.receivers_correspondent))
        if self.intermediary:
            L.append(render_institution_A("56A", self.intermediary))
        if self.account_with_institution:
            L.append(render_institution_A("57A", self.account_with_institution))
        L.append(render_institution_A("58A", self.beneficiary_institution))
        if self.sender_to_receiver_info:
            L.append(render_narrative("72", self.sender_to_receiver_info, 6))
        # Sequence B — underlying customer credit transfer (COV only)
        if self.cover:
            L.append(render_customer_50K(self.ordering_customer))
            if self.account_with_institution:
                L.append(render_institution_A("57A", self.account_with_institution))
            L.append(render_beneficiary_59(self.beneficiary_customer))
            if self.cov_remittance_info:
                L.append(render_narrative("70", self.cov_remittance_info))
        L.append("-}")
        return "\n".join(L)

    def build(self) -> str:
        mt = "202"
        if self.cover:
            # COV is MT202 with validation flag {119:COV} in block 3
            b3 = "{3:{119:COV}{121:" + self.uetr + "}}"
        else:
            b3 = block3(self.uetr)
        return (block1(self.sender_bic)
                + block2_input(mt, self.receiver_bic)
                + b3
                + "\n" + self.block4() + "\n"
                + block5())


# --------------------------------------------------------------------------- #
# Minimal tag parser (for round-trip sanity checks / debugging)
# --------------------------------------------------------------------------- #

def extract_block4_tags(message: str) -> dict:
    m = re.search(r"\{4:(.*?)-\}", message, re.S)
    if not m:
        return {}
    tags: dict = {}
    for tm in re.finditer(r":(\d{2}[A-Z]?):((?:(?!\n:)[\s\S])*)", m.group(1)):
        tags.setdefault(tm.group(1), []).append(tm.group(2).strip("\n"))
    return tags
