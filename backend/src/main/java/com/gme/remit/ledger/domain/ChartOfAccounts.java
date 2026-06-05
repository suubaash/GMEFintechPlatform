package com.gme.remit.ledger.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** VIII.4 seed Chart of Accounts: role -> accounting type. Account code is {@code ROLE-CCY}. */
public final class ChartOfAccounts {

    public static final Map<String, AccountType> ROLE_TYPE = new LinkedHashMap<>();

    static {
        ROLE_TYPE.put("OPERATOR_NOSTRO", AccountType.ASSET);
        ROLE_TYPE.put("PARTNER_PREFUND", AccountType.ASSET);
        ROLE_TYPE.put("IN_TRANSIT_PAYOUT", AccountType.ASSET);
        ROLE_TYPE.put("SUSPENSE_CLEARING", AccountType.ASSET);
        ROLE_TYPE.put("CUSTOMER_WALLET", AccountType.LIABILITY);
        ROLE_TYPE.put("SAFEGUARDING_CLIENT_MONEY", AccountType.LIABILITY);
        ROLE_TYPE.put("UNAPPLIED_RECEIPTS", AccountType.LIABILITY);
        ROLE_TYPE.put("EQUITY", AccountType.EQUITY);
        ROLE_TYPE.put("FEE_INCOME", AccountType.INCOME);
        ROLE_TYPE.put("FX_PNL", AccountType.INCOME);
        ROLE_TYPE.put("LIQUIDITY_COST", AccountType.EXPENSE);
        ROLE_TYPE.put("PARTNER_PAYOUT_COST", AccountType.EXPENSE);
        ROLE_TYPE.put("RAIL_FEES", AccountType.EXPENSE);
    }

    private ChartOfAccounts() {
    }

    public static boolean isKnownRole(String role) {
        return ROLE_TYPE.containsKey(role);
    }

    public static AccountType typeOf(String role) {
        AccountType t = ROLE_TYPE.get(role);
        if (t == null) {
            throw new IllegalArgumentException("unknown account role " + role);
        }
        return t;
    }

    public static String accountCode(String role, String currency) {
        return role + "-" + currency.toUpperCase();
    }
}
