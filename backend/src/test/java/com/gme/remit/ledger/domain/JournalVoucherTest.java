package com.gme.remit.ledger.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalVoucherTest {

    @Test
    void balancedJvBuildsWithPostings() {
        JournalVoucher jv = JournalVoucher.create("PAYIN", "KRW", List.of(
                new JournalVoucher.Line("OPERATOR_NOSTRO-KRW", Direction.DEBIT, 1_000_000),
                new JournalVoucher.Line("CUSTOMER_WALLET-KRW", Direction.CREDIT, 1_000_000)
        ), null, null, LocalDate.now(), true);

        assertThat(jv.getAmountMinor()).isEqualTo(1_000_000);
        assertThat(jv.getPostings()).hasSize(2);
    }

    @Test
    void unbalancedJvIsRejected() {
        assertThatThrownBy(() -> JournalVoucher.create("PAYIN", "KRW", List.of(
                new JournalVoucher.Line("OPERATOR_NOSTRO-KRW", Direction.DEBIT, 1_000_000),
                new JournalVoucher.Line("CUSTOMER_WALLET-KRW", Direction.CREDIT, 999_999)
        ), null, null, LocalDate.now(), true))
                .isInstanceOf(LedgerUnbalancedException.class);
    }

    @Test
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> JournalVoucher.create("FEE", "KRW", List.of(
                new JournalVoucher.Line("CUSTOMER_WALLET-KRW", Direction.DEBIT, 0),
                new JournalVoucher.Line("FEE_INCOME-KRW", Direction.CREDIT, 0)
        ), null, null, LocalDate.now(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
