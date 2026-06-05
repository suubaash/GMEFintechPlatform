package com.gme.remit.ledger.domain;

/** Raised when a journal voucher's debits do not equal its credits. */
public class LedgerUnbalancedException extends RuntimeException {
    public LedgerUnbalancedException(String message) {
        super(message);
    }
}
