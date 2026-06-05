package com.gme.remit.transfer.domain;

/** Appendix B.1 money-movement taxonomy (recorded on each journal voucher). */
public enum MovementType {
    PAYIN, FEE, CONVERSION_SELL, CONVERSION_BUY, PAYOUT, SETTLEMENT,
    RETURN, REFUND, REVERSAL, ADJUSTMENT, PREFUND_DRAWDOWN, PREFUND_TOPUP
}
