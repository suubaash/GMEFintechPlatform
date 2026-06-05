package com.gme.remit.swift;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mt103Test {

    private Mt103 valid() {
        return Mt103.builder()
                .sender("GMEBKRSEXXX")
                .receiver("NABLNPKAXXX")
                .reference("TXN0000000000001")
                .valueDate(LocalDate.of(2026, 6, 4))
                .money("NPR", new BigDecimal("100174.50"))
                .ordering(SwiftCustomer.of("Maria Garcia", null, "KR"))
                .beneficiary(SwiftCustomer.of("Ram Thapa", "0211939494", "NP"))
                .accountWith(SwiftInstitution.ofBic("NABLNPKAXXX", "Nabil Bank"))
                .remittance("REMITTANCE TXN1");
    }

    @Test
    void buildsWellFormedMt103() {
        String fin = valid().build();
        assertThat(fin).contains("{1:F01GMEBKRSE");
        assertThat(fin).contains("{2:I103NABLNPKA");
        assertThat(fin).contains("{121:"); // UETR in block 3
        assertThat(fin).contains(":20:TXN0000000000001");
        assertThat(fin).contains(":23B:CRED");
        assertThat(fin).contains(":32A:260604NPR100174,50"); // yymmdd + ccy + comma-decimal amount
        assertThat(fin).contains(":59:/0211939494");
        assertThat(fin).contains("Ram Thapa");
        assertThat(fin).contains(":71A:SHA");
        assertThat(fin).contains("{5:{CHK:");
    }

    @Test
    void rejectsBadBic() {
        assertThatThrownBy(() -> valid().receiver("BADBIC").build())
                .isInstanceOf(SwiftFormatException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> valid().money("NPR", BigDecimal.ZERO).build())
                .isInstanceOf(SwiftFormatException.class);
    }
}
