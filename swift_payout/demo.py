"""demo.py — build the KR -> Nepal (Globe IME) payout and print the SWIFT messages.

Uses the running example from the PRD discussion: a customer credit to Globe IME account
0211939494. BICs here are ILLUSTRATIVE placeholders, not real institution identifiers.
"""
from datetime import date
from payout_service import PayoutService, PayoutRequest, PartyIn


def build_example() -> PayoutService:
    svc = PayoutService()
    req = PayoutRequest(
        transfer_ref="TXN20260603A",
        leg_id="LEG2PAYOUT",
        idempotency_key="idem-9f2c-0001",
        amount_minor=920605,            # NPR 9,206.05 (2 dp)
        currency="NPR",
        value_date=date(2026, 6, 3),
        ordering_customer=PartyIn(
            name="HONG GILDONG",
            account="KR29010203040506",
            address_lines=["123 TEHERAN-RO, GANGNAM-GU", "SEOUL"],
            country="KR",
        ),
        beneficiary_customer=PartyIn(
            name="RAM BAHADUR THAPA",
            account="0211939494",
            address_lines=["WARD 5, KATHMANDU"],
            country="NP",
        ),
        # ----- routing (illustrative BICs) -----
        ordering_institution_bic="OPERKRSEXXX",   # operator's KR bank
        beneficiary_bank_bic="GLBBNPKAXXX",       # Globe IME Bank (account-with-institution)
        receiver_bic="CORRUSNYXXX",               # USD/NPR correspondent receiving the MT103
        senders_correspondent_bic="CORRUSNYXXX",
        charges="SHA",
        remittance_info="/INV/REMITTANCE FAMILY SUPPORT",
        regulatory_reporting="/BENEFRES/NP",
        purpose_code="FAML",
        send_cover=True,
        cover_receiver_bic="CORRUSNYXXX",
        cover_beneficiary_institution_bic="GLBBNPKAXXX",
    )
    svc.create_payout(req)
    return svc


if __name__ == "__main__":
    svc = build_example()
    res = list(svc._by_idem.values())[0]
    print(f"payout_id        : {res.payout_id}")
    print(f"canonical_status : {res.canonical_status}")
    print(f"UETR             : {res.uetr}")
    print(f"MT103 :20:       : {res.sender_reference}")
    print(f"MT202 :20:       : {res.cover_reference}")
    print("\n================= MT103 (customer credit) =================\n")
    print(res.messages["MT103"])
    print("\n=============== MT202 COV (cover / settlement) ===============\n")
    print(res.messages["MT202COV"])
