"""ISO 20022 return/repair reason codes + native mapping (WS1-08)."""
ISO20022 = {"AC01", "AC04", "AC06", "AM04", "BE04", "MD07", "RR04", "NARR"}

NATIVE_MAP = {
    "INVALID_ACCOUNT": "AC01",
    "ACCOUNT_CLOSED": "AC04",
    "ACCOUNT_BLOCKED": "AC06",
    "INSUFFICIENT_FUNDS": "AM04",
    "NAME_MISMATCH": "BE04",
    "DECEASED": "MD07",
    "REGULATORY": "RR04",
}


def map_return_code(native: str) -> str:
    """Map a partner reason to ISO 20022; unknown -> 'other'."""
    return NATIVE_MAP.get((native or "").upper(), "other")
