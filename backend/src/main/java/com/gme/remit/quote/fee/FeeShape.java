package com.gme.remit.quote.fee;

/** Appendix A fee shapes. */
public enum FeeShape {
    FLAT,    // fixed minor-unit amount
    PERCENT  // basis points of the send amount, optionally min/max capped
}
