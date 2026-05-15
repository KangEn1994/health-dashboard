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


def test_store_merges_new_default_workout_catalog_and_plans(tmp_path: Path) -> None:
    legacy_catalog = {
        "parts": [
            {"id": "chest", "label": "胸部", "color": "#ef4444", "sort_order": 10, "active": True},
        ],
        "exercises": {
            "chest": [
                {
                    "id": "bench_press",
                    "name": "平板卧推",
                    "description": "legacy",
                    "detail_placeholder": "legacy",
                    "active": True,
                    "sort_order": 10,
                }
            ]
        },
    }
    legacy_plans = [
        {
            "id": "push_a",
            "name": "推日 A",
            "description": "legacy",
            "groups": [],
            "active": True,
        }
    ]
    tmp_path.mkdir(parents=True, exist_ok=True)
    (tmp_path / "workout_catalog.json").write_text(json.dumps(legacy_catalog, ensure_ascii=False), encoding="utf-8")
    (tmp_path / "workout_plans.json").write_text(json.dumps(legacy_plans, ensure_ascii=False), encoding="utf-8")

    store = JsonStore(data_dir=tmp_path)
    catalog = store.get_workout_catalog()
    plans = store.get_workout_plans()

    assert any(part["id"] == "cardio" for part in catalog["parts"])
    assert any(exercise["id"] == "elliptical" for exercise in catalog["exercises"]["cardio"])
    assert any(plan["id"] == "cardio_recovery" for plan in plans)
