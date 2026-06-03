"""Idempotency store + execute helper (WS1-07). In-memory reference (Redis in prod)."""
from __future__ import annotations
import threading
import time
from dataclasses import dataclass
from typing import Any, Callable, Optional


@dataclass
class _Entry:
    value: Any
    expires_at: float


class InMemoryIdempotencyStore:
    def __init__(self, ttl_seconds: int = 86400):
        self.ttl = ttl_seconds
        self._d = {}
        self._lock = threading.Lock()

    def get(self, key: str) -> Optional[Any]:
        with self._lock:
            e = self._d.get(key)
            if e and e.expires_at > time.time():
                return e.value
            if e:
                del self._d[key]
            return None

    def set(self, key: str, value: Any):
        with self._lock:
            self._d[key] = _Entry(value, time.time() + self.ttl)

    def execute(self, key: str, fn: Callable[[], Any]):
        """Run fn once per key; replays return the stored result. Returns (value, replayed)."""
        with self._lock:
            e = self._d.get(key)
            if e and e.expires_at > time.time():
                return e.value, True
            value = fn()
            self._d[key] = _Entry(value, time.time() + self.ttl)
            return value, False
