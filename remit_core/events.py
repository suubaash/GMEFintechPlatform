"""Domain-event envelope + idempotency key + in-memory bus (WS1-05, WS1-06)."""
from __future__ import annotations
import hashlib
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable, Dict, List

EVENT_VERSION = 1


@dataclass
class EventEnvelope:
    event_type: str
    resource_type: str
    resource_id: str
    payload: dict
    tenant_id: str = "default"
    sequence: int = 0
    trace_id: str = ""
    event_version: int = EVENT_VERSION
    event_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    occurred_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    recorded_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    def partition_key(self) -> str:
        return self.resource_id

    def validate(self) -> bool:
        for f in ("event_type", "resource_type", "resource_id"):
            if not getattr(self, f):
                raise ValueError(f"event envelope missing {f}")
        return True


def derive_idempotency_key(resource_type: str, resource_id: str, event_type: str, sequence: int) -> str:
    raw = f"{resource_type}:{resource_id}:{event_type}:{sequence}"
    return hashlib.sha256(raw.encode()).hexdigest()[:32]


class InMemoryEventBus:
    """Reference bus: ordered-per-resource semantics + dedupe by event_id."""
    def __init__(self):
        self._subs: Dict[str, List[Callable]] = {}
        self._seen = set()
        self.delivered: List[EventEnvelope] = []

    def subscribe(self, event_type: str, handler: Callable):
        self._subs.setdefault(event_type, []).append(handler)

    def publish(self, env: EventEnvelope) -> bool:
        env.validate()
        if env.event_id in self._seen:
            return False  # dedupe
        self._seen.add(env.event_id)
        self.delivered.append(env)
        for h in self._subs.get(env.event_type, []):
            h(env)
        return True
