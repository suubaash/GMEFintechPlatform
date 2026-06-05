package com.gme.remit.swift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MT103 — Single Customer Credit Transfer. Builds the FIN message text (blocks 1–5) with the
 * mandatory fields and the common optional ones, with SWIFT-style formatting and validation.
 * Reference implementation: output is ready to hand to a SWIFT gateway, not a substitute for full
 * SWIFT Standards Release network validation.
 */
public class Mt103 {

    private String senderBic;
    private String receiverBic;
    private String senderReference;
    private LocalDate valueDate;
    private String currency;
    private BigDecimal amount;
    private SwiftCustomer orderingCustomer;
    private SwiftCustomer beneficiaryCustomer;
    private SwiftInstitution accountWithInstitution;
    private String remittanceInfo;
    private String bankOperationCode = "CRED";
    private String detailsOfCharges = "SHA";
    private String uetr = UUID.randomUUID().toString();

    public static Mt103 builder() {
        return new Mt103();
    }

    public Mt103 sender(String bic) { this.senderBic = bic; return this; }
    public Mt103 receiver(String bic) { this.receiverBic = bic; return this; }
    public Mt103 reference(String ref) { this.senderReference = ref; return this; }
    public Mt103 valueDate(LocalDate d) { this.valueDate = d; return this; }
    public Mt103 money(String ccy, BigDecimal major) { this.currency = ccy; this.amount = major; return this; }
    public Mt103 ordering(SwiftCustomer c) { this.orderingCustomer = c; return this; }
    public Mt103 beneficiary(SwiftCustomer c) { this.beneficiaryCustomer = c; return this; }
    public Mt103 accountWith(SwiftInstitution i) { this.accountWithInstitution = i; return this; }
    public Mt103 remittance(String text) { this.remittanceInfo = text; return this; }
    public Mt103 uetr(String u) { this.uetr = u; return this; }

    public String getUetr() {
        return uetr;
    }

    public List<String> validate() {
        List<String> errs = new ArrayList<>();
        if (!List.of("OUR", "SHA", "BEN").contains(detailsOfCharges)) {
            errs.add(":71A: must be OUR/SHA/BEN");
        }
        if (amount == null || amount.signum() <= 0) {
            errs.add(":32A: amount must be > 0");
        }
        try {
            SwiftFormat.checkBic(senderBic, "sender");
            SwiftFormat.checkBic(receiverBic, "receiver");
        } catch (SwiftFormatException e) {
            errs.add(e.getMessage());
        }
        if (beneficiaryCustomer == null || beneficiaryCustomer.name() == null
                || beneficiaryCustomer.name().isBlank()) {
            errs.add(":59: beneficiary name required");
        }
        return errs;
    }

    public String build() {
        List<String> errs = validate();
        if (!errs.isEmpty()) {
            throw new SwiftFormatException("MT103 invalid: " + String.join("; ", errs));
        }
        String block1 = "{1:F01" + SwiftFormat.ltAddress(senderBic) + "0000" + "000000}";
        String block2 = "{2:I103" + SwiftFormat.ltAddress(receiverBic) + "N}";
        String block3 = "{3:{121:" + uetr + "}}";
        String block5 = "{5:{CHK:000000000000}}";
        return block1 + block2 + block3 + "\n" + block4() + "\n" + block5;
    }

    private String block4() {
        List<String> l = new ArrayList<>();
        l.add("{4:");
        l.add(":20:" + SwiftFormat.ref16(senderReference));
        l.add(":23B:" + bankOperationCode);
        l.add(":32A:" + SwiftFormat.valueDate(valueDate) + currency.toUpperCase()
                + SwiftFormat.amount(amount, currency));
        l.add(renderCustomer50K(orderingCustomer));
        if (accountWithInstitution != null) {
            l.add(renderInstitution57A(accountWithInstitution));
        }
        l.add(renderBeneficiary59(beneficiaryCustomer));
        if (remittanceInfo != null && !remittanceInfo.isBlank()) {
            l.add(":70:" + SwiftFormat.checkCharset(
                    String.join("\n", SwiftFormat.truncateLines(List.of(remittanceInfo), 4, 35)), ":70:"));
        }
        l.add(":71A:" + detailsOfCharges);
        l.add("-}");
        return String.join("\n", l);
    }

    private static String renderCustomer50K(SwiftCustomer c) {
        List<String> lines = new ArrayList<>();
        if (c.account() != null) {
            lines.add("/" + c.account());
        }
        List<String> body = new ArrayList<>();
        body.add(c.name());
        body.addAll(c.addressLines());
        lines.addAll(SwiftFormat.truncateLines(body, 4 - lines.size(), 35));
        return ":50K:" + SwiftFormat.checkCharset(String.join("\n", lines), ":50K:");
    }

    private static String renderBeneficiary59(SwiftCustomer c) {
        List<String> lines = new ArrayList<>();
        if (c.account() != null) {
            lines.add("/" + c.account());
        }
        List<String> tail = new ArrayList<>(c.addressLines());
        if (c.country() != null) {
            tail.add(c.country());
        }
        List<String> body = new ArrayList<>();
        body.add(c.name());
        body.addAll(tail);
        lines.addAll(SwiftFormat.truncateLines(body, 4 - lines.size(), 35));
        return ":59:" + SwiftFormat.checkCharset(String.join("\n", lines), ":59:");
    }

    private static String renderInstitution57A(SwiftInstitution inst) {
        String out = "";
        if (inst.account() != null) {
            out += "/" + inst.account() + "\n";
        }
        out += SwiftFormat.checkBic(inst.bic(), "57A");
        return ":57A:" + out;
    }
}
