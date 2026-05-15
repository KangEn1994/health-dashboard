from __future__ import annotations

import json
import os
import tempfile
from copy import deepcopy
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock
from typing import Any

from app.defaults import (
    DEFAULT_ENTRIES,
    DEFAULT_METRICS,
    DEFAULT_PROFILE,
    DEFAULT_WORKOUT_CATALOG,
    DEFAULT_WORKOUT_PLANS,
    DEFAULT_WORKOUT_SESSIONS,
)


def default_data_dir() -> Path:
    configured = os.getenv("HEALTH_DASHBOARD_DATA_DIR")
    if configured:
        return Path(configured).expanduser().resolve()
    return (Path(__file__).resolve().parent / "data").resolve()


@dataclass
class JsonStore:
    data_dir: Path = field(default_factory=default_data_dir)

    def __post_init__(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self._lock = Lock()
        self._cache: dict[str, Any] = {}
        self._mtime_cache: dict[str, float] = {}
        self._ensure_defaults()

    @property
    def profile_path(self) -> Path:
        return self.data_dir / "profile.json"

    @property
    def metrics_path(self) -> Path:
        return self.data_dir / "metrics.json"

    @property
    def entries_path(self) -> Path:
        return self.data_dir / "entries.json"

    @property
    def workout_catalog_path(self) -> Path:
        return self.data_dir / "workout_catalog.json"

    @property
    def workout_plans_path(self) -> Path:
        return self.data_dir / "workout_plans.json"

    @property
    def workout_sessions_path(self) -> Path:
        return self.data_dir / "workout_sessions.json"

    def _ensure_defaults(self) -> None:
        defaults = {
            self.profile_path: DEFAULT_PROFILE,
            self.metrics_path: DEFAULT_METRICS,
            self.entries_path: DEFAULT_ENTRIES,
            self.workout_catalog_path: DEFAULT_WORKOUT_CATALOG,
            self.workout_plans_path: DEFAULT_WORKOUT_PLANS,
            self.workout_sessions_path: DEFAULT_WORKOUT_SESSIONS,
        }
        for path, payload in defaults.items():
            if not path.exists():
                self._atomic_write(path, payload)

    def _atomic_write(self, path: Path, payload: Any) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd, temp_name = tempfile.mkstemp(dir=str(path.parent), prefix=path.name, suffix=".tmp")
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        os.replace(temp_name, path)

    def _read_json(self, path: Path) -> Any:
        current_mtime = path.stat().st_mtime
        cache_key = str(path)
        cached_mtime = self._mtime_cache.get(cache_key)
        if cached_mtime == current_mtime:
            return deepcopy(self._cache[cache_key])
        with path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
        self._cache[cache_key] = data
        self._mtime_cache[cache_key] = current_mtime
        return deepcopy(data)

    def _write_json(self, path: Path, payload: Any) -> Any:
        with self._lock:
            self._atomic_write(path, payload)
            cache_key = str(path)
            self._cache[cache_key] = deepcopy(payload)
            self._mtime_cache[cache_key] = path.stat().st_mtime
        return deepcopy(payload)

    def get_profile(self) -> dict[str, Any]:
        return self._read_json(self.profile_path)

    def update_profile(self, profile: dict[str, Any]) -> dict[str, Any]:
        return self._write_json(self.profile_path, profile)

    def get_metrics(self) -> list[dict[str, Any]]:
        return self._read_json(self.metrics_path)

    def save_metrics(self, metrics: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._write_json(self.metrics_path, metrics)

    def get_entries(self) -> list[dict[str, Any]]:
        return self._read_json(self.entries_path)

    def save_entries(self, entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._write_json(self.entries_path, entries)

    def get_workout_catalog(self) -> dict[str, Any]:
        return self._read_json(self.workout_catalog_path)

    def save_workout_catalog(self, catalog: dict[str, Any]) -> dict[str, Any]:
        return self._write_json(self.workout_catalog_path, catalog)

    def get_workout_plans(self) -> list[dict[str, Any]]:
        return self._read_json(self.workout_plans_path)

    def save_workout_plans(self, plans: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._write_json(self.workout_plans_path, plans)

    def get_workout_sessions(self) -> list[dict[str, Any]]:
        return self._read_json(self.workout_sessions_path)

    def save_workout_sessions(self, sessions: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._write_json(self.workout_sessions_path, sessions)
