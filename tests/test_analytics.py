from __future__ import annotations

from datetime import UTC, datetime, timedelta

from app import analytics


def build_series() -> list[dict]:
    base = datetime.now(UTC) - timedelta(days=6)
    return [
        {"recorded_at": (base + timedelta(days=index)).isoformat(), "value": float(index + 1)}
        for index in range(7)
    ]


def test_moving_average() -> None:
    series = build_series()
    result = analytics.moving_average(series, 3)
    assert result[-1]["value"] == 6.0


def test_plateau_detection() -> None:
    series = [
        {"recorded_at": (datetime.now(UTC) - timedelta(days=10 - index)).isoformat(), "value": 70.0 + index * 0.01}
        for index in range(10)
    ]
    result = analytics.plateau_detection(series, 14, 0.05)
    assert result["is_plateau"] is True


def test_pearson_correlation() -> None:
    correlation = analytics.pearson_correlation([1, 2, 3], [2, 4, 6])
    assert correlation == 1.0


def test_beijing_time_conversion() -> None:
    value = "2026-05-14T00:30:00+00:00"
    converted = analytics.parse_recorded_at_beijing(value)
    assert converted.isoformat().startswith("2026-05-14T08:30:00+08:00")


def test_period_change_bundle() -> None:
    now = analytics.now_beijing()
    series = [
        {"recorded_at": now.replace(month=1, day=2, hour=8, minute=0, second=0, microsecond=0).isoformat(), "value": 75.0},
        {"recorded_at": now.replace(month=max(now.month, 4), day=2, hour=8, minute=0, second=0, microsecond=0).isoformat(), "value": 73.5},
        {"recorded_at": now.replace(day=max(now.day, 2), hour=8, minute=0, second=0, microsecond=0).isoformat(), "value": 72.9},
    ]
    bundle = analytics.period_change_bundle(series)
    assert any(item["label"] in {"本年", "本季度"} for item in bundle)


def test_workout_recommendations_include_frequency_signal() -> None:
    catalog = {
        "parts": [
            {"id": "chest", "label": "胸部", "active": True},
            {"id": "back", "label": "背部", "active": True},
            {"id": "cardio", "label": "有氧", "active": True},
        ],
        "exercises": {},
    }
    plans = [{"id": "push_a", "active": True}]
    sessions = [
        {
            "recorded_at": (analytics.now_beijing() - timedelta(days=3)).isoformat(),
            "plan_id": "push_a",
            "exercises": [{"part_id": "chest", "exercise_id": "bench_press", "sets": 4}],
        }
    ]
    result = analytics.workout_recommendations(catalog, plans, sessions)
    assert any("训练次数偏少" in item or "没有覆盖" in item for item in result)
    assert any("有氧" in item for item in result)
