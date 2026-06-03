"""
payout_service.py — maps the platform's normalized payout model onto SWIFT MT103/MT202.

In PRD terms this is the SWIFT connector running in `code_adapter` mode (VIII.6.3 escape
hatch): it implements the payout slice of the adapter SPI (VIII.6.1) —
  submitPayout(leg, instrument) -> normalized result with provider refs + canonical status.

It does NOT transmit over SWIFTNet; it returns the FIN message(s) ready for a gateway, with a
canonical status of SUBMITTED (handed off). A real gateway ACK/NAK or gpi update would later
drive CONFIRMED / REJECTED / RETURNED / UNKNOWN (Appendix B §4).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from typing import Dict, Optional

from swift_mt import (
    Customer, Institution, MT103, MT202, minor_to_decimal, ref16, SwiftFormatError,
)

# Canonical payment status set (Appendix B §4)
CANONICAL_STATUSES = {
    "PENDING_SUBMISSION", "SUBMITTED", "ACCEPTED", "IN_PROGRESS",
    "CONFIRMED", "REJECTED", "RETURNED", "FAILED", "CANCELLED", "UNKNOWN",
}


@dataclass
class PartyIn:
    name: str
    account: Optional[str] = None
    address_lines: list = field(default_factory=list)
    country: Optional[str] = None


@dataclass
class PayoutRequest:
    """Normalized payout request (aligned to Transfer/Leg/Party/Instrument/Money)."""
    transfer_ref: str
    leg_id: str
    idempotency_key: str
    amount_minor: int
    currency: str
    value_date: date
    # Parties
    ordering_customer: PartyIn
    beneficiary_customer: PartyIn
    # Routing (BICs). Illustrative placeholders unless wired to a real correspondent network.
    ordering_institution_bic: str          # the sending/operator bank (:52A:)
    beneficiary_bank_bic: str              # account-with-institution (:57A:) — e.g. Globe IME
    receiver_bic: str                      # who receives the MT103 (correspondent / AWI)
    senders_correspondent_bic: Optional[str] = None   # :53A:
    intermediary_bic: Optional[str] = None             # :56A:
    # Charges & info
    charges: str = "SHA"                   # OUR/SHA/BEN
    remittance_info: Optional[str] = None
    regulatory_reporting: Optional[str] = None
    purpose_code: Optional[str] = None
    # Cover (settlement) leg
    send_cover: bool = True
    cover_receiver_bic: Optional[str] = None     # receiver of the MT202 COV
    cover_beneficiary_institution_bic: Optional[str] = None  # :58A: (defaults to AWI)


@dataclass
class PayoutResult:
    payout_id: str
    idempotency_key: str
    canonical_status: str
    uetr: str
    sender_reference: str             # MT103 :20:
    cover_reference: Optional[str]    # MT202 :20:
    messages: Dict[str, str]          # {"MT103": "...", "MT202COV": "..."}
    errors: list = field(default_factory=list)


def _cust(p: PartyIn) -> Customer:
    return Customer(name=p.name, account=p.account,
                    address_lines=list(p.address_lines), country=p.country)


class PayoutService:
    """Builds SWIFT payout messages from normalized requests, idempotently."""

    def __init__(self) -> None:
        self._by_idem: Dict[str, PayoutResult] = {}
        self._seq = 0

    def create_payout(self, req: PayoutRequest) -> PayoutResult:
        # Idempotency: same key -> same result (no double-send). Ties to ProviderRequest
        # (partner_id, idempotency_key) uniqueness in the PRD (V.6.2).
        if req.idempotency_key in self._by_idem:
            return self._by_idem[req.idempotency_key]

        self._seq += 1
        payout_id = f"po_{self._seq:08d}"
        amount = minor_to_decimal(req.amount_minor, req.currency)
        # :20: from the leg; :21:/cover :20: derived deterministically.
        mt103_ref = ref16(req.transfer_ref + "-" + req.leg_id)
        cover_ref = ref16("CScover-" + req.leg_id)

        awi = Institution(bic=req.beneficiary_bank_bic)

        try:
            mt103 = MT103(
                sender_bic=req.ordering_institution_bic,
                receiver_bic=req.receiver_bic,
                sender_reference=mt103_ref,
                value_date=req.value_date,
                currency=req.currency,
                amount=amount,
                ordering_customer=_cust(req.ordering_customer),
                beneficiary_customer=_cust(req.beneficiary_customer),
                details_of_charges=req.charges,
                ordering_institution=Institution(bic=req.ordering_institution_bic),
                senders_correspondent=(Institution(bic=req.senders_correspondent_bic)
                                       if req.senders_correspondent_bic else None),
                intermediary_institution=(Institution(bic=req.intermediary_bic)
                                          if req.intermediary_bic else None),
                account_with_institution=awi,
                remittance_info=req.remittance_info,
                regulatory_reporting=req.regulatory_reporting,
            )
            messages = {"MT103": mt103.build()}
            cover_reference = None

            if req.send_cover:
                ben_inst_bic = req.cover_beneficiary_institution_bic or req.beneficiary_bank_bic
                mt202 = MT202(
                    sender_bic=req.ordering_institution_bic,
                    receiver_bic=(req.cover_receiver_bic
                                  or req.senders_correspondent_bic
                                  or req.receiver_bic),
                    transaction_reference=cover_ref,
                    related_reference=mt103_ref,           # links the cover to the MT103
                    value_date=req.value_date,
                    currency=req.currency,
                    amount=amount,
                    beneficiary_institution=Institution(bic=ben_inst_bic),
                    ordering_institution=Institution(bic=req.ordering_institution_bic),
                    senders_correspondent=(Institution(bic=req.senders_correspondent_bic)
                                           if req.senders_correspondent_bic else None),
                    account_with_institution=awi,
                    cover=True,
                    ordering_customer=_cust(req.ordering_customer),
                    beneficiary_customer=_cust(req.beneficiary_customer),
                    cov_remittance_info=req.remittance_info,
                    uetr=mt103.uetr,                       # cover shares the payment UETR
                )
                messages["MT202COV"] = mt202.build()
                cover_reference = cover_ref

            result = PayoutResult(
                payout_id=payout_id,
                idempotency_key=req.idempotency_key,
                canonical_status="SUBMITTED",   # handed to the SWIFT gateway
                uetr=mt103.uetr,
                sender_reference=mt103_ref,
                cover_reference=cover_reference,
                messages=messages,
            )
        except SwiftFormatError as e:
            # Bad/unmappable input -> REJECTED (a validation failure, not UNKNOWN).
            result = PayoutResult(
                payout_id=payout_id,
                idempotency_key=req.idempotency_key,
                canonical_status="REJECTED",
                uetr="",
                sender_reference=mt103_ref,
                cover_reference=None,
                messages={},
                errors=[str(e)],
            )

        self._by_idem[req.idempotency_key] = result
        return result
