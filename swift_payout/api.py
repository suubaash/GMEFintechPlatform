"""
api.py — HTTP layer for the SWIFT payout service.

Run:
    pip install fastapi uvicorn pydantic
    uvicorn api:app --reload
Then POST /v1/payouts with the JSON body shown in README.md.

This exposes the normalized payout endpoint; the body maps 1:1 onto PayoutRequest, and the
response carries the canonical status plus the generated MT103 / MT202COV message text.
"""
from __future__ import annotations

from datetime import date
from typing import List, Optional, Dict

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from payout_service import PayoutService, PayoutRequest, PartyIn

app = FastAPI(title="SWIFT MT Payout API", version="1.0.0")
_service = PayoutService()


class PartyModel(BaseModel):
    name: str
    account: Optional[str] = None
    address_lines: List[str] = Field(default_factory=list)
    country: Optional[str] = None


class PayoutRequestModel(BaseModel):
    transfer_ref: str
    leg_id: str
    amount_minor: int = Field(gt=0)
    currency: str
    value_date: date
    ordering_customer: PartyModel
    beneficiary_customer: PartyModel
    ordering_institution_bic: str
    beneficiary_bank_bic: str
    receiver_bic: str
    senders_correspondent_bic: Optional[str] = None
    intermediary_bic: Optional[str] = None
    charges: str = "SHA"
    remittance_info: Optional[str] = None
    regulatory_reporting: Optional[str] = None
    purpose_code: Optional[str] = None
    send_cover: bool = True
    cover_receiver_bic: Optional[str] = None
    cover_beneficiary_institution_bic: Optional[str] = None


class PayoutResponse(BaseModel):
    payout_id: str
    canonical_status: str
    uetr: str
    sender_reference: str
    cover_reference: Optional[str]
    messages: Dict[str, str]
    errors: List[str]


@app.post("/v1/payouts", response_model=PayoutResponse, status_code=201)
def create_payout(body: PayoutRequestModel,
                  idempotency_key: str = Header(..., alias="X-Idempotency-Key")):
    req = PayoutRequest(
        transfer_ref=body.transfer_ref,
        leg_id=body.leg_id,
        idempotency_key=idempotency_key,
        amount_minor=body.amount_minor,
        currency=body.currency,
        value_date=body.value_date,
        ordering_customer=PartyIn(**body.ordering_customer.model_dump()),
        beneficiary_customer=PartyIn(**body.beneficiary_customer.model_dump()),
        ordering_institution_bic=body.ordering_institution_bic,
        beneficiary_bank_bic=body.beneficiary_bank_bic,
        receiver_bic=body.receiver_bic,
        senders_correspondent_bic=body.senders_correspondent_bic,
        intermediary_bic=body.intermediary_bic,
        charges=body.charges,
        remittance_info=body.remittance_info,
        regulatory_reporting=body.regulatory_reporting,
        purpose_code=body.purpose_code,
        send_cover=body.send_cover,
        cover_receiver_bic=body.cover_receiver_bic,
        cover_beneficiary_institution_bic=body.cover_beneficiary_institution_bic,
    )
    result = _service.create_payout(req)
    if result.canonical_status == "REJECTED":
        raise HTTPException(status_code=422, detail={"errors": result.errors})
    return PayoutResponse(
        payout_id=result.payout_id,
        canonical_status=result.canonical_status,
        uetr=result.uetr,
        sender_reference=result.sender_reference,
        cover_reference=result.cover_reference,
        messages=result.messages,
        errors=result.errors,
    )


@app.get("/health")
def health():
    return {"status": "ok"}
