# remit-platform — KR-hub Remittance Platform (reference implementation)

A growing, tested reference implementation of the platform specified in `PRD.md` (Parts I–XI).
This is the **buildable core**, not a production deployment: in-memory/SQLite stand in for
Kafka/Redis/PostgreSQL behind the same interfaces; live partner integrations, infra provisioning,
and regulatory values are out of scope (they need real environments/contracts).

## Packages

| Package | WBS | Status |
|---|---|---|
| `remit_core/` | WS1 (+WS0-14) | ✅ Canonical foundations — Money, Appendix B enums, Transfer/Leg state machines, event envelope + bus, idempotency, ISO-20022 codes, typed errors. 25 tests. |
| `remit_ledger/` | WS2 | ✅ Double-entry operational ledger on SQLite — append-only (engine triggers), balanced-JV invariant, typed balances (Ledger/Reserved/Available/Cleared), reservations, statement, suspense, GL events. 12 tests incl. the KR→NP golden-path. |
| `swift_payout/` | VIII.15 / WS7 | ✅ SWIFT MT103 + MT202/COV payout serializer + service + FastAPI. 18 tests. |

Next per the WBS: WS3 (transfers/routing/quoting) → WS4 (FX) → WS5 (provider gateway) → wire the
golden path end-to-end (quote → transfer → ledger postings → SWIFT payout).

## Run

```bash
python -m unittest discover -s tests -v      # core + ledger (37 tests)
cd swift_payout && python -m unittest -v      # SWIFT rail (18 tests)
python swift_payout/demo.py                   # prints a sample MT103 + MT202 COV
```

## Layout
```
remit_core/     foundations (pure stdlib)
remit_ledger/   double-entry ledger (sqlite3)
swift_payout/   SWIFT MT payout rail (+ FastAPI)
tests/          unit tests for core + ledger
PRD.md          the full specification (Parts I–XI) + WBS
```
