from __future__ import annotations

import json
import time
from pathlib import Path

from app.repository import JsonStore


def test_store_creates_default_files(tmp_path: Path) -> None:
    store = JsonStore(data_dir=tmp_path)
    assert (tmp_path / "profile.json").exists()
    assert (tmp_path / "metrics.json").exists()
    assert (tmp_path / "entries.json").exists()
    assert (tmp_path / "workout_catalog.json").exists()
    assert (tmp_path / "workout_plans.json").exists()
    assert (tmp_path / "workout_sessions.json").exists()
    assert store.get_metrics()
    assert store.get_workout_catalog()["parts"]


def test_store_reload_after_external_edit(tmp_path: Path) -> None:
    store = JsonStore(data_dir=tmp_path)
    profile = store.get_profile()
    profile["height_cm"] = 176
    time.sleep(0.01)
    with (tmp_path / "profile.json").open("w", encoding="utf-8") as handle:
        json.dump(profile, handle)
    reloaded = store.get_profile()
    assert reloaded["height_cm"] == 176
