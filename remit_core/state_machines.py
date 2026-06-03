"""Transfer & Leg state machines (WS1-03, WS1-04) over the Appendix B states."""
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Dict, List, Set
from .enums import TransferStatus as T, LegStatus as L
from .errors import IllegalTransition


@dataclass
class Transition:
    from_state: str
    to_state: str
    at: datetime
    cause: str = ""


class StateMachine:
    def __init__(self, name: str, table: Dict[object, Set[object]], terminal: Set[object]):
        self.name = name
        self.table = table
        self.terminal = terminal

    def can(self, frm, to) -> bool:
        return to in self.table.get(frm, set())

    def apply(self, frm, to, cause: str = "", history: List[Transition] = None):
        if not self.can(frm, to):
            raise IllegalTransition(
                f"{self.name}: {getattr(frm,'name',frm)} -> {getattr(to,'name',to)} is not allowed")
        if history is not None:
            history.append(Transition(getattr(frm, "name", frm), getattr(to, "name", to),
                                       datetime.now(timezone.utc), cause))
        return to

    def is_terminal(self, state) -> bool:
        return state in self.terminal


TRANSFER_TABLE = {
    T.SUBMITTED:        {T.FUNDS_RECEIVED, T.ON_HOLD, T.FAILED, T.CANCELLED},
    T.FUNDS_RECEIVED:   {T.PROCESSING, T.ON_HOLD, T.FAILED},
    T.PROCESSING:       {T.PAYOUT_INITIATED, T.ON_HOLD, T.FAILED, T.RETURNED},
    T.PAYOUT_INITIATED: {T.COMPLETED, T.RETURNED, T.FAILED, T.ON_HOLD},
    T.ON_HOLD:          {T.FUNDS_RECEIVED, T.PROCESSING, T.PAYOUT_INITIATED, T.CANCELLED, T.FAILED},
    T.RETURNED:         {T.REFUNDED},
    T.COMPLETED: set(), T.REFUNDED: set(), T.FAILED: set(), T.CANCELLED: set(),
}
TRANSFER_TERMINAL = {T.COMPLETED, T.REFUNDED, T.FAILED, T.CANCELLED}

LEG_TABLE = {
    L.CREATED:   {L.PENDING, L.IN_FLIGHT, L.FAILED},
    L.PENDING:   {L.IN_FLIGHT, L.FAILED},
    L.IN_FLIGHT: {L.CONFIRMED, L.RETURNED, L.FAILED},
    L.CONFIRMED: {L.SETTLED, L.RETURNED},
    L.SETTLED:   {L.RETURNED},
    L.RETURNED:  {L.REVERSED},
    L.FAILED: set(), L.REVERSED: set(),
}
LEG_TERMINAL = {L.FAILED, L.REVERSED}

TRANSFER_SM = StateMachine("transfer", TRANSFER_TABLE, TRANSFER_TERMINAL)
LEG_SM = StateMachine("leg", LEG_TABLE, LEG_TERMINAL)
