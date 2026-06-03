# SWIFT MT Payout API (MT103 + MT202 / MT202 COV)

A runnable reference payout service that turns the platform's **normalized payout request**
(Transfer / Leg / Party / Instrument / Money) into **SWIFT FIN messages**:

- **MT103** — single customer credit transfer (credits the beneficiary customer)
- **MT202 / MT202 COV** — general financial-institution transfer / **cover** (the settlement leg
  to the correspondent, carrying the underlying customer details in sequence B)

It returns the message **text** plus a **canonical payment status** and references, ready to hand to
a SWIFT gateway / service bureau.

## Scope & limits (read this)

- ✅ Builds Blocks 1–5 with SWIFT-style formatting: decimal-comma amounts (per ISO-4217 minor units),
  `YYMMDD` value dates, `'x'` charset checks, BIC length checks, `4*35x` party/narrative blocks,
  UETR (`{121:}`), COV validation flag (`{119:COV}`).
- ✅ Covers mandatory + common optional tags (see mapping below) and validates required fields.
- ✅ Idempotent: same `X-Idempotency-Key` → same payout (no double-send), mirroring the PRD's
  `ProviderRequest(partner_id, idempotency_key)` uniqueness (V.6.2).
- ❌ Does **not** transmit over SWIFTNet (needs SWIFT accreditation + Alliance/network or a bureau).
- ❌ Is **not** a substitute for full SWIFT Standards-Release field/network validation (MVR/SR) or
  BIC-directory verification. The `{5:{CHK:...}}` trailer is a placeholder the interface computes on
  transmission.
- ⚠️ BICs in the demo are **illustrative placeholders**, not real institution identifiers.

## Files

| File | Role |
|---|---|
| `swift_mt.py` | Core: formatters, validators, `MT103`, `MT202` (+COV) builders, header/trailer blocks, tag parser. Stdlib only. |
| `payout_service.py` | Maps the normalized `PayoutRequest` → MT103/MT202COV; idempotency; canonical status. (The SWIFT connector in `code_adapter` mode.) |
| `api.py` | FastAPI HTTP layer: `POST /v1/payouts`. |
| `demo.py` | Builds the KR→Nepal (Globe IME `0211939494`) example and prints both messages. |
| `test_swift_mt.py` | 18 unit tests (`python -m unittest -v`). |

## Run

```bash
python demo.py                  # prints a sample MT103 + MT202 COV
python -m unittest -v           # run tests (no pytest needed)

pip install fastapi uvicorn pydantic
uvicorn api:app --reload        # then POST /v1/payouts
```

Example request:

```bash
curl -X POST localhost:8000/v1/payouts \
  -H 'X-Idempotency-Key: idem-0001' -H 'Content-Type: application/json' \
  -d '{
    "transfer_ref":"TXN20260603A","leg_id":"LEG2PAYOUT",
    "amount_minor":920605,"currency":"NPR","value_date":"2026-06-03",
    "ordering_customer":{"name":"HONG GILDONG","account":"KR29010203040506","country":"KR"},
    "beneficiary_customer":{"name":"RAM BAHADUR THAPA","account":"0211939494","country":"NP"},
    "ordering_institution_bic":"OPERKRSEXXX","beneficiary_bank_bic":"GLBBNPKAXXX",
    "receiver_bic":"CORRUSNYXXX","senders_correspondent_bic":"CORRUSNYXXX",
    "remittance_info":"/INV/FAMILY SUPPORT","send_cover":true }'
```

## Field mapping (normalized model → SWIFT tags)

| Normalized field | MT103 | MT202 COV |
|---|---|---|
| `transfer_ref`+`leg_id` | `:20:` Sender's Reference | `:21:` Related Reference (links to the MT103) |
| derived cover ref | — | `:20:` Transaction Reference |
| `bank_operation_code` | `:23B:` (CRED) | — |
| `value_date`+`currency`+`amount_minor` | `:32A:` | `:32A:` |
| `ordering_customer` (Party) | `:50K:` | seq B `:50K:` |
| `ordering_institution_bic` | `:52A:` | `:52A:` |
| `senders_correspondent_bic` | `:53A:` | `:53A:` |
| `intermediary_bic` | `:56A:` | `:56A:` |
| `beneficiary_bank_bic` (account-with-institution) | `:57A:` | `:57A:` (+ seq B) |
| `cover_beneficiary_institution_bic` | — | `:58A:` Beneficiary Institution |
| `beneficiary_customer` (Party, incl. account `0211939494`) | `:59:` | seq B `:59:` |
| `remittance_info` | `:70:` | seq B `:70:` |
| `charges` | `:71A:` (OUR/SHA/BEN) | — |
| `regulatory_reporting` | `:77B:` | — |
| (generated) | `{121:}` UETR | `{119:COV}` + shared `{121:}` |

## How it fits the PRD

- It implements the **payout slice of the adapter SPI** (`submitPayout`, VIII.6.1) and emits the
  **canonical payment status set** (Appendix B §4): generation → `SUBMITTED`; a gateway ACK/NAK or
  gpi update would later drive `CONFIRMED` / `REJECTED` / `RETURNED`, and an ambiguous/no-status case
  → `UNKNOWN` → reconciliation (never auto-retried).
- SWIFT FIN is precisely the kind of protocol the **no-code connector DSL cannot express**, so this
  is the **`code_adapter` escape hatch** described in VIII.6.3. A connector definition would declare
  `mode: code_adapter` and point at this implementation, while the rest of the platform (routing,
  ledger postings per V.4.7, idempotency, recon) is unchanged.
- Suggested PRD home if you want it formalized: a feature **4.2.x "SWIFT MT payout rail (MT103 +
  MT202/COV)"** under Korea/Additional rails, with acceptance criteria mirroring the validations and
  the status mapping here.
