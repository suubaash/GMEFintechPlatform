"""Unit tests for remit_core (WS1 leaf done-checks). Run: python -m unittest -v"""
import threading
import unittest
from decimal import Decimal

from remit_core import (Money, minor_units, MovementType, TransferStatus, LegStatus,
                        PaymentStatus, RefundFamily, PartnerRole, BalanceType,
                        TRANSFER_SM, LEG_SM, EventEnvelope, InMemoryEventBus,
                        derive_idempotency_key, InMemoryIdempotencyStore, map_return_code)
from remit_core.errors import CurrencyMismatch, ValidationError, IllegalTransition


class TestMoney(unittest.TestCase):                       # WS1-01
    def test_minor_units(self):
        self.assertEqual(minor_units("KRW"), 0); self.assertEqual(minor_units("NPR"), 2)
        self.assertEqual(minor_units("KWD"), 3); self.assertEqual(minor_units("zzz"), 2)
    def test_from_major_and_back(self):
        self.assertEqual(Money.from_major("9206.05", "NPR").amount_minor, 920605)
        self.assertEqual(Money.from_major("100000", "KRW").amount_minor, 100000)
        self.assertEqual(Money(920605, "NPR").to_major(), Decimal("9206.05"))
    def test_rounding_half_up(self):
        self.assertEqual(Money.from_major("1.005", "USD").amount_minor, 101)
    def test_add_sub_same_currency(self):
        self.assertEqual((Money(100, "USD") + Money(50, "USD")).amount_minor, 150)
        self.assertEqual((Money(100, "USD") - Money(150, "USD")).amount_minor, -50)
    def test_mixed_currency_raises(self):
        with self.assertRaises(CurrencyMismatch): Money(1, "USD") + Money(1, "NPR")
    def test_mul_by_int_only(self):
        self.assertEqual((Money(100, "USD") * 3).amount_minor, 300)
        with self.assertRaises(ValidationError): Money(100, "USD") * 1.5
    def test_amount_minor_must_be_int(self):
        with self.assertRaises(ValidationError): Money(1.5, "USD")
    def test_immutable(self):
        m = Money(1, "USD")
        with self.assertRaises(Exception): m.amount_minor = 2


class TestEnums(unittest.TestCase):                       # WS1-02
    def test_payment_status_members(self):
        self.assertEqual({s.value for s in PaymentStatus},
            {"PENDING_SUBMISSION","SUBMITTED","ACCEPTED","IN_PROGRESS","CONFIRMED",
             "REJECTED","RETURNED","FAILED","CANCELLED","UNKNOWN"})
    def test_refund_families_distinct(self):
        self.assertEqual({r.value for r in RefundFamily}, {"RETURN","REFUND","RECALL","REVERSAL"})
    def test_partner_roles(self):
        self.assertEqual({r.value for r in PartnerRole}, {"PAYIN","PAYOUT","FX","NOSTRO"})
    def test_balance_types(self):
        self.assertIn("AVAILABLE", {b.value for b in BalanceType})


class TestStateMachines(unittest.TestCase):               # WS1-03 / WS1-04
    def test_transfer_legal(self):
        hist = []
        nxt = TRANSFER_SM.apply(TransferStatus.SUBMITTED, TransferStatus.FUNDS_RECEIVED, "payin", hist)
        self.assertEqual(nxt, TransferStatus.FUNDS_RECEIVED)
        self.assertEqual(hist[0].to_state, "FUNDS_RECEIVED")
    def test_transfer_illegal(self):
        with self.assertRaises(IllegalTransition):
            TRANSFER_SM.apply(TransferStatus.SUBMITTED, TransferStatus.COMPLETED)
    def test_transfer_terminal(self):
        self.assertTrue(TRANSFER_SM.is_terminal(TransferStatus.COMPLETED))
        self.assertFalse(TRANSFER_SM.is_terminal(TransferStatus.PROCESSING))
    def test_leg_flow(self):
        self.assertTrue(LEG_SM.can(LegStatus.IN_FLIGHT, LegStatus.CONFIRMED))
        self.assertFalse(LEG_SM.can(LegStatus.CREATED, LegStatus.SETTLED))
    def test_hold_resume(self):
        self.assertTrue(TRANSFER_SM.can(TransferStatus.PROCESSING, TransferStatus.ON_HOLD))
        self.assertTrue(TRANSFER_SM.can(TransferStatus.ON_HOLD, TransferStatus.PROCESSING))


class TestEvents(unittest.TestCase):                      # WS1-05 / WS1-06
    def test_envelope_validate(self):
        e = EventEnvelope("transfer.created", "transfer", "t1", {"x": 1})
        self.assertTrue(e.validate()); self.assertEqual(e.partition_key(), "t1")
    def test_envelope_missing_field(self):
        with self.assertRaises(ValueError):
            EventEnvelope("", "transfer", "t1", {}).validate()
    def test_idem_key_deterministic(self):
        a = derive_idempotency_key("transfer", "t1", "created", 0)
        b = derive_idempotency_key("transfer", "t1", "created", 0)
        self.assertEqual(a, b)
    def test_bus_dedupe(self):
        bus = InMemoryEventBus(); seen = []
        bus.subscribe("transfer.created", lambda e: seen.append(e.event_id))
        e = EventEnvelope("transfer.created", "transfer", "t1", {})
        self.assertTrue(bus.publish(e))
        self.assertFalse(bus.publish(e))     # replay deduped
        self.assertEqual(len(seen), 1)


class TestIdempotency(unittest.TestCase):                 # WS1-07
    def test_replay_returns_stored(self):
        store = InMemoryIdempotencyStore(); calls = []
        def fn(): calls.append(1); return "result"
        v1, r1 = store.execute("k", fn); v2, r2 = store.execute("k", fn)
        self.assertEqual((v1, v2), ("result", "result"))
        self.assertFalse(r1); self.assertTrue(r2); self.assertEqual(len(calls), 1)
    def test_concurrent_single_effect(self):
        store = InMemoryIdempotencyStore(); counter = {"n": 0}
        def fn():
            counter["n"] += 1; return counter["n"]
        threads = [threading.Thread(target=lambda: store.execute("k", fn)) for _ in range(20)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual(counter["n"], 1)   # one effect under race


class TestReturnCodes(unittest.TestCase):                 # WS1-08
    def test_known(self):
        self.assertEqual(map_return_code("INVALID_ACCOUNT"), "AC01")
        self.assertEqual(map_return_code("name_mismatch"), "BE04")
    def test_unknown(self):
        self.assertEqual(map_return_code("weird"), "other")


if __name__ == "__main__":
    unittest.main(verbosity=2)
