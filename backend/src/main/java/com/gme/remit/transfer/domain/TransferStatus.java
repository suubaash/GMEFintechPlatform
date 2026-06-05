package com.gme.remit.transfer.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Appendix B.2 transfer states + the allowed-transition table (mirrors TRANSFER_TABLE). */
public enum TransferStatus {
    SUBMITTED, FUNDS_RECEIVED, PROCESSING, PAYOUT_INITIATED, COMPLETED,
    ON_HOLD, RETURNED, REFUNDED, FAILED, CANCELLED;

    private static final Map<TransferStatus, Set<TransferStatus>> TABLE = Map.of(
            SUBMITTED, EnumSet.of(FUNDS_RECEIVED, ON_HOLD, FAILED, CANCELLED),
            FUNDS_RECEIVED, EnumSet.of(PROCESSING, ON_HOLD, FAILED),
            PROCESSING, EnumSet.of(PAYOUT_INITIATED, ON_HOLD, FAILED, RETURNED),
            PAYOUT_INITIATED, EnumSet.of(COMPLETED, RETURNED, FAILED, ON_HOLD),
            ON_HOLD, EnumSet.of(FUNDS_RECEIVED, PROCESSING, PAYOUT_INITIATED, CANCELLED, FAILED),
            RETURNED, EnumSet.of(REFUNDED),
            COMPLETED, EnumSet.noneOf(TransferStatus.class),
            REFUNDED, EnumSet.noneOf(TransferStatus.class),
            FAILED, EnumSet.noneOf(TransferStatus.class),
            CANCELLED, EnumSet.noneOf(TransferStatus.class)
    );

    public boolean canTransitionTo(TransferStatus to) {
        return TABLE.getOrDefault(this, Set.of()).contains(to);
    }

    public boolean isTerminal() {
        return TABLE.getOrDefault(this, Set.of()).isEmpty();
    }
}
