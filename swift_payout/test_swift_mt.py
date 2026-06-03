"""test_swift_mt.py — run with:  python -m unittest -v   (no pytest needed)."""
import unittest
from datetime import date
from decimal import Decimal

from swift_mt import (
    format_amount, format_value_date, minor_to_decimal, check_bic, ref16,
    SwiftFormatError, extract_block4_tags, MT103, MT202, Customer, Institution,
)
from payout_service import PayoutService, PayoutRequest, PartyIn
from demo import build_example


class TestFormatting(unittest.TestCase):
    def test_amount_two_dp(self):
        self.assertEqual(format_amount(Decimal("9206.05"), "NPR"), "9206,05")

    def test_amount_zero_dp_keeps_trailing_comma(self):
        self.assertEqual(format_amount(Decimal("100000"), "KRW"), "100000,")

    def test_amount_three_dp(self):
        self.assertEqual(format_amount(Decimal("12.5"), "KWD"), "12,500")

    def test_minor_to_decimal(self):
        self.assertEqual(minor_to_decimal(920605, "NPR"), Decimal("9206.05"))
        self.assertEqual(minor_to_decimal(100000, "KRW"), Decimal("100000"))

    def test_value_date(self):
        self.assertEqual(format_value_date(date(2026, 6, 3)), "260603")

    def test_bic_lengths(self):
        self.assertEqual(check_bic("glbbnpka", "x"), "GLBBNPKA")
        self.assertEqual(check_bic("OPERKRSEXXX", "x"), "OPERKRSEXXX")
        with self.assertRaises(SwiftFormatError):
            check_bic("SHORT", "x")

    def test_ref16(self):
        self.assertEqual(ref16("/TXN//123/"), "TXN/123")
        self.assertLessEqual(len(ref16("A" * 40)), 16)
        with self.assertRaises(SwiftFormatError):
            ref16("////")


class TestMT103(unittest.TestCase):
    def _mt(self, **over):
        base = dict(
            sender_bic="OPERKRSEXXX", receiver_bic="CORRUSNYXXX",
            sender_reference="REF123", value_date=date(2026, 6, 3),
            currency="NPR", amount=Decimal("9206.05"),
            ordering_customer=Customer("HONG GILDONG", account="KR29010203040506"),
            beneficiary_customer=Customer("RAM THAPA", account="0211939494", country="NP"),
            account_with_institution=Institution("GLBBNPKAXXX"),
        )
        base.update(over)
        return MT103(**base)

    def test_builds_and_has_blocks(self):
        msg = self._mt().build()
        for blk in ("{1:F01", "{2:I103", "{3:", "{4:", "-}", "{5:"):
            self.assertIn(blk, msg)

    def test_mandatory_tags_present(self):
        tags = extract_block4_tags(self._mt().build())
        for t in ("20", "23B", "32A", "50K", "59", "71A"):
            self.assertIn(t, tags, f"missing :{t}:")

    def test_32A_format(self):
        tags = extract_block4_tags(self._mt().build())
        self.assertEqual(tags["32A"][0], "260603NPR9206,05")

    def test_beneficiary_account_line(self):
        tags = extract_block4_tags(self._mt().build())
        self.assertTrue(tags["59"][0].startswith("/0211939494"))

    def test_invalid_charges_rejected(self):
        with self.assertRaises(SwiftFormatError):
            self._mt(details_of_charges="ZZZ").build()

    def test_uetr_in_block3(self):
        self.assertIn(":121:" if False else "{121:", self._mt().build())


class TestMT202COV(unittest.TestCase):
    def _cov(self):
        return MT202(
            sender_bic="OPERKRSEXXX", receiver_bic="CORRUSNYXXX",
            transaction_reference="COVREF1", related_reference="REF123",
            value_date=date(2026, 6, 3), currency="NPR", amount=Decimal("9206.05"),
            beneficiary_institution=Institution("GLBBNPKAXXX"),
            ordering_institution=Institution("OPERKRSEXXX"),
            account_with_institution=Institution("GLBBNPKAXXX"),
            cover=True,
            ordering_customer=Customer("HONG GILDONG", account="KR29010203040506"),
            beneficiary_customer=Customer("RAM THAPA", account="0211939494", country="NP"),
        )

    def test_cov_flag_and_seqB(self):
        msg = self._cov().build()
        self.assertIn("{119:COV}", msg)
        tags = extract_block4_tags(msg)
        # seq A institution + seq B customer fields both present
        self.assertIn("58A", tags)
        self.assertIn("50K", tags)
        self.assertIn("59", tags)
        self.assertEqual(tags["21"][0], "REF123")

    def test_cov_requires_customers(self):
        m = self._cov()
        m.ordering_customer = None
        with self.assertRaises(SwiftFormatError):
            m.build()


class TestPayoutService(unittest.TestCase):
    def test_example_builds_both_messages(self):
        svc = build_example()
        res = list(svc._by_idem.values())[0]
        self.assertEqual(res.canonical_status, "SUBMITTED")
        self.assertIn("MT103", res.messages)
        self.assertIn("MT202COV", res.messages)

    def test_idempotency_same_result(self):
        svc = PayoutService()
        req = PayoutRequest(
            transfer_ref="T1", leg_id="L1", idempotency_key="k1",
            amount_minor=920605, currency="NPR", value_date=date(2026, 6, 3),
            ordering_customer=PartyIn("HONG GILDONG", account="KR29"),
            beneficiary_customer=PartyIn("RAM THAPA", account="0211939494", country="NP"),
            ordering_institution_bic="OPERKRSEXXX", beneficiary_bank_bic="GLBBNPKAXXX",
            receiver_bic="CORRUSNYXXX",
        )
        r1 = svc.create_payout(req)
        r2 = svc.create_payout(req)
        self.assertEqual(r1.payout_id, r2.payout_id)          # no double-send
        self.assertEqual(r1.uetr, r2.uetr)

    def test_bad_bic_rejected(self):
        svc = PayoutService()
        req = PayoutRequest(
            transfer_ref="T2", leg_id="L2", idempotency_key="k2",
            amount_minor=920605, currency="NPR", value_date=date(2026, 6, 3),
            ordering_customer=PartyIn("A"), beneficiary_customer=PartyIn("B", account="1"),
            ordering_institution_bic="BAD", beneficiary_bank_bic="GLBBNPKAXXX",
            receiver_bic="CORRUSNYXXX",
        )
        res = svc.create_payout(req)
        self.assertEqual(res.canonical_status, "REJECTED")
        self.assertTrue(res.errors)


if __name__ == "__main__":
    unittest.main(verbosity=2)
