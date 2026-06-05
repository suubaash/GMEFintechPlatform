package com.gme.remit.transfer.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Appendix B.3 leg states + allowed-transition table (mirrors LEG_TABLE). */
public enum LegStatus {
    CREATED, PENDING, IN_FLIGHT, CONFIRMED, SETTLED, RETURNED, FAILED, REVERSED;

    private static final Map<LegStatus, Set<LegStatus>> TABLE = Map.of(
            CREATED, EnumSet.of(PENDING, IN_FLIGHT, FAILED),
            PENDING, EnumSet.of(IN_FLIGHT, FAILED),
            IN_FLIGHT, EnumSet.of(CONFIRMED, RETURNED, FAILED),
            CONFIRMED, EnumSet.of(SETTLED, RETURNED),
            SETTLED, EnumSet.of(RETURNED),
            RETURNED, EnumSet.of(REVERSED),
            FAILED, EnumSet.noneOf(LegStatus.class),
            REVERSED, EnumSet.noneOf(LegStatus.class)
    );

    public boolean canTransitionTo(LegStatus to) {
        return TABLE.getOrDefault(this, Set.of()).contains(to);
    }

    public boolean isTerminal() {
        return TABLE.getOrDefault(this, Set.of()).isEmpty();
    }
}
