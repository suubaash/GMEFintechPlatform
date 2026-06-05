package com.gme.remit.ledger.domain;

/** Accounting types; ASSET/EXPENSE are debit-normal, the rest credit-normal. */
public enum AccountType {
    ASSET, LIABILITY, EQUITY, INCOME, EXPENSE;

    public Direction normalSide() {
        return (this == ASSET || this == EXPENSE) ? Direction.DEBIT : Direction.CREDIT;
    }
}
