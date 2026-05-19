from __future__ import annotations

import math
from collections import defaultdict
from datetime import UTC, date, datetime, time, timedelta
from statistics import mean
from typing import Any
from zoneinfo import ZoneInfo


RANGE_MAP = {
    "month": 30,
    "quarter": 90,
    "year": 365,
    "30d": 30,
    "90d": 90,
    "365d": 365,
    "all": None,
}

BEIJING_TZ = ZoneInfo("Asia/Shanghai")
BUSINESS_DAY_START_HOUR = 6


def parse_range(range_name: str) -> int | None:
    if range_name not in RANGE_MAP:
        raise ValueError("Unsupported range")
    return RANGE_MAP[range_name]


def parse_recorded_at(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(UTC)


def to_beijing(dt: datetime) -> datetime:
    return dt.astimezone(BEIJING_TZ)


def now_beijing() -> datetime:
    return datetime.now(BEIJING_TZ)


def parse_recorded_at_beijing(value: str) -> datetime:
    return to_beijing(parse_recorded_at(value))


def business_date_from_dt(dt: datetime) -> date:
    beijing_time = to_beijing(dt)
    return (beijing_time - timedelta(hours=BUSINESS_DAY_START_HOUR)).date()


def business_date(value: str) -> date:
    return business_date_from_dt(parse_recorded_at(value))


def business_day_start(day: date) -> datetime:
    return datetime.combine(day, time(hour=BUSINESS_DAY_START_HOUR), tzinfo=BEIJING_TZ)


def today_business_date() -> date:
    return business_date_from_dt(now_beijing())


def sort_entries(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return sorted(entries, key=lambda item: parse_recorded_at(item["recorded_at"]))


def filter_entries_by_days(entries: list[dict[str, Any]], days: int | None) -> list[dict[str, Any]]:
    if days is None:
        return entries
    cutoff = today_business_date() - timedelta(days=days)
    return [entry for entry in entries if business_date(entry["recorded_at"]) >= cutoff]


def bmi_for_entry(entry: dict[str, Any], height_cm: float) -> float | None:
    weight = entry.get("values", {}).get("weight_kg")
    if weight is None or not height_cm:
        return None
    height_m = height_cm / 100
    if height_m <= 0:
        return None
    return round(float(weight) / (height_m * height_m), 1)


def series_for_metric(
    entries: list[dict[str, Any]],
    metric_id: str,
    height_cm: float | None = None,
) -> list[dict[str, Any]]:
    series = []
    for entry in sort_entries(entries):
        if metric_id == "bmi":
            value = bmi_for_entry(entry, height_cm or 0)
        else:
            value = entry.get("values", {}).get(metric_id)
        if isinstance(value, (int, float)):
            series.append({"recorded_at": entry["recorded_at"], "value": float(value)})
    return series


def cardio_part_ids(catalog: dict[str, Any] | None = None) -> set[str]:
    result = {"cardio"}
    for part in (catalog or {}).get("parts", []):
        part_id = str(part.get("id", ""))
        label = str(part.get("label", ""))
        if "cardio" in part_id.lower() or "有氧" in label:
            result.add(part_id)
    return result


def is_cardio_exercise(exercise: dict[str, Any], cardio_ids: set[str]) -> bool:
    part_id = exercise.get("part_id")
    return bool(part_id) and part_id in cardio_ids


def workout_duration_series(
    sessions: list[dict[str, Any]],
    days: int | None = None,
    catalog: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    relevant_sessions = sessions
    today = today_business_date()
    start_date = None
    cardio_ids = cardio_part_ids(catalog)
    if days is not None:
        start_date = today - timedelta(days=days)
        relevant_sessions = [session for session in sessions if business_date(session["recorded_at"]) >= start_date]

    buckets: dict[str, int] = defaultdict(int)
    for session in relevant_sessions:
        day_key = business_date(session["recorded_at"]).isoformat()
        buckets[day_key] += sum(
            int(exercise.get("duration_minutes") or 0)
            for exercise in session.get("exercises", [])
            if is_cardio_exercise(exercise, cardio_ids)
        )

    if start_date is None and buckets:
        start_date = min(datetime.fromisoformat(day).date() for day in buckets)

    if start_date is not None:
        total_days = (today - start_date).days
        return [
            {
                "recorded_at": business_day_start(start_date + timedelta(days=offset)).isoformat(),
                "value": float(buckets.get((start_date + timedelta(days=offset)).isoformat(), 0)),
            }
            for offset in range(total_days + 1)
        ]

    return [
        {"recorded_at": business_day_start(datetime.fromisoformat(day).date()).isoformat(), "value": float(total_minutes)}
        for day, total_minutes in sorted(buckets.items())
    ]


def moving_average(series: list[dict[str, Any]], window: int = 7) -> list[dict[str, Any]]:
    result = []
    values = [point["value"] for point in series]
    for index, point in enumerate(series):
        start = max(0, index - window + 1)
        window_values = values[start : index + 1]
        result.append({"recorded_at": point["recorded_at"], "value": round(mean(window_values), 3)})
    return result


def summary_for_series(series: list[dict[str, Any]]) -> dict[str, Any]:
    if not series:
        return {
            "latest": None,
            "delta": None,
            "average_7d": None,
            "average_30d": None,
            "min": None,
            "max": None,
        }
    latest = series[-1]["value"]
    delta = round(latest - series[-2]["value"], 3) if len(series) >= 2 else None

    now = now_beijing()
    business_today = business_date_from_dt(now)
    last_7 = [item["value"] for item in series if business_date(item["recorded_at"]) >= business_today - timedelta(days=7)]
    last_30 = [item["value"] for item in series if business_date(item["recorded_at"]) >= business_today - timedelta(days=30)]
    return {
        "latest": latest,
        "delta": delta,
        "average_7d": round(mean(last_7), 3) if last_7 else None,
        "average_30d": round(mean(last_30), 3) if last_30 else None,
        "min": min(item["value"] for item in series),
        "max": max(item["value"] for item in series),
    }


def pearson_correlation(points_a: list[float], points_b: list[float]) -> float | None:
    if len(points_a) != len(points_b) or len(points_a) < 2:
        return None
    mean_a = mean(points_a)
    mean_b = mean(points_b)
    numerator = sum((a - mean_a) * (b - mean_b) for a, b in zip(points_a, points_b))
    denominator_a = math.sqrt(sum((a - mean_a) ** 2 for a in points_a))
    denominator_b = math.sqrt(sum((b - mean_b) ** 2 for b in points_b))
    if denominator_a == 0 or denominator_b == 0:
        return None
    return round(numerator / (denominator_a * denominator_b), 4)


def correlation_pairs(
    entries: list[dict[str, Any]],
    metric_ids: list[str],
    height_cm: float,
    workout_duration_by_day: dict[str, float] | None = None,
) -> list[dict[str, Any]]:
    sorted_entries = sort_entries(entries)
    metric_pairs = []
    primary = "weight_kg" if "weight_kg" in metric_ids else None
    candidates = [metric for metric in metric_ids if metric != primary]
    if primary is None:
        return []
    for metric_id in candidates:
        points = []
        primary_values = []
        secondary_values = []
        for entry in sorted_entries:
            weight = entry.get("values", {}).get(primary)
            if metric_id == "bmi":
                other = bmi_for_entry(entry, height_cm)
            elif metric_id == "workout_duration_min":
                day_key = business_date(entry["recorded_at"]).isoformat()
                other = (workout_duration_by_day or {}).get(day_key)
            else:
                other = entry.get("values", {}).get(metric_id)
            if isinstance(weight, (int, float)) and isinstance(other, (int, float)):
                primary_values.append(float(weight))
                secondary_values.append(float(other))
                points.append({"x": float(weight), "y": float(other), "recorded_at": entry["recorded_at"]})
        metric_pairs.append(
            {
                "metric_id": metric_id,
                "correlation": pearson_correlation(primary_values, secondary_values),
                "points": points,
            }
        )
    return metric_pairs


def continuity_stats(entries: list[dict[str, Any]]) -> dict[str, Any]:
    if not entries:
        return {"record_days": 0, "longest_streak": 0, "days_since_last_entry": None}
    dates = sorted({business_date(entry["recorded_at"]) for entry in entries})
    longest = current = 1
    for previous, current_date in zip(dates, dates[1:]):
        if current_date - previous == timedelta(days=1):
            current += 1
        else:
            longest = max(longest, current)
            current = 1
    longest = max(longest, current)
    days_since_last = (today_business_date() - dates[-1]).days
    return {
        "record_days": len(dates),
        "longest_streak": longest,
        "days_since_last_entry": days_since_last,
    }


def calendar_heatmap(entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    counts: dict[str, int] = defaultdict(int)
    for entry in entries:
        key = business_date(entry["recorded_at"]).isoformat()
        counts[key] += 1
    return [{"date": date, "count": count} for date, count in sorted(counts.items())]


def period_average_comparison(series: list[dict[str, Any]], days: int) -> dict[str, Any]:
    if days <= 0:
        return {"current": None, "previous": None, "delta": None}
    current_start = today_business_date() - timedelta(days=days)
    previous_start = current_start - timedelta(days=days)
    current_values = [item["value"] for item in series if business_date(item["recorded_at"]) >= current_start]
    previous_values = [
        item["value"]
        for item in series
        if previous_start <= business_date(item["recorded_at"]) < current_start
    ]
    current_avg = round(mean(current_values), 3) if current_values else None
    previous_avg = round(mean(previous_values), 3) if previous_values else None
    delta = round(current_avg - previous_avg, 3) if current_avg is not None and previous_avg is not None else None
    return {"current": current_avg, "previous": previous_avg, "delta": delta}


def weekly_changes(series: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if len(series) < 2:
        return []
    week_buckets: dict[str, list[float]] = defaultdict(list)
    for point in series:
        iso_year, iso_week, _ = business_date(point["recorded_at"]).isocalendar()
        week_buckets[f"{iso_year}-W{iso_week:02d}"].append(point["value"])
    changes = []
    for week, values in sorted(week_buckets.items()):
        changes.append({"week": week, "change": round(values[-1] - values[0], 3)})
    return changes


def plateau_detection(series: list[dict[str, Any]], days: int = 14, threshold: float = 0.05) -> dict[str, Any]:
    if len(series) < 2:
        return {"is_plateau": False, "slope": None, "window_days": days}
    cutoff = today_business_date() - timedelta(days=days)
    window = [point for point in series if business_date(point["recorded_at"]) >= cutoff]
    if len(window) < 2:
        return {"is_plateau": False, "slope": None, "window_days": days}
    first = window[0]
    last = window[-1]
    days_span = max((business_date(last["recorded_at"]) - business_date(first["recorded_at"])).days, 1)
    slope = (last["value"] - first["value"]) / days_span
    return {"is_plateau": abs(slope) < threshold, "slope": round(slope, 4), "window_days": days}


def current_period_change(series: list[dict[str, Any]], period: str) -> dict[str, Any] | None:
    if len(series) < 2:
        return None
    now = now_beijing()
    business_today = business_date_from_dt(now)
    if period == "week":
        start = business_today - timedelta(days=business_today.weekday())
        label = "本周"
    elif period == "month":
        start = business_today.replace(day=1)
        label = "本月"
    elif period == "quarter":
        quarter_month = ((business_today.month - 1) // 3) * 3 + 1
        start = business_today.replace(month=quarter_month, day=1)
        label = "本季度"
    elif period == "year":
        start = business_today.replace(month=1, day=1)
        label = "本年"
    else:
        raise ValueError("Unsupported period")

    window = [point for point in series if business_date(point["recorded_at"]) >= start]
    if len(window) < 2:
        return None
    change = round(window[-1]["value"] - window[0]["value"], 3)
    return {
        "label": label,
        "start_value": window[0]["value"],
        "end_value": window[-1]["value"],
        "change": change,
        "points": len(window),
    }


def period_change_bundle(series: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result = []
    for period in ("week", "month", "quarter", "year"):
        change = current_period_change(series, period)
        if change is not None:
            result.append(change)
    return result


def _recent_window(series: list[dict[str, Any]], days: int = 30) -> list[dict[str, Any]]:
    start_date = today_business_date() - timedelta(days=days - 1)
    return [point for point in series if business_date(point["recorded_at"]) >= start_date]


def _range_change_insight(
    series: list[dict[str, Any]],
    label: str,
    unit: str,
    decrease_label: str,
    increase_label: str,
) -> str | None:
    window = _recent_window(series, 30)
    if not window:
        return None
    values = [point["value"] for point in window]
    highest = max(values)
    lowest = min(values)
    if len(window) >= 2:
        delta = round(window[0]["value"] - window[-1]["value"], 3)
        direction = decrease_label if delta >= 0 else increase_label
        change_text = f"30天累计{direction} {abs(delta):.1f}{unit}"
    else:
        change_text = "记录不足，暂无法计算30天累计变化"
    return f"最近30天最高{label} {highest:.1f}{unit}，最低{label} {lowest:.1f}{unit}，{change_text}。"


def _fat_mass_insight(weight_series: list[dict[str, Any]], body_fat_series: list[dict[str, Any]]) -> str | None:
    weights_by_day = {business_date(point["recorded_at"]): point["value"] for point in _recent_window(weight_series, 30)}
    body_fat_by_day = {business_date(point["recorded_at"]): point["value"] for point in _recent_window(body_fat_series, 30)}
    common_days = sorted(set(weights_by_day) & set(body_fat_by_day))
    if len(common_days) < 2:
        return None
    first_day = common_days[0]
    last_day = common_days[-1]
    first_fat_mass = weights_by_day[first_day] * body_fat_by_day[first_day] / 100
    last_fat_mass = weights_by_day[last_day] * body_fat_by_day[last_day] / 100
    fat_loss = first_fat_mass - last_fat_mass
    direction = "减少" if fat_loss >= 0 else "增加"
    return f"最近30天估算脂肪重量由 {first_fat_mass:.1f}kg 变为 {last_fat_mass:.1f}kg，实际{direction} {abs(fat_loss):.1f}kg。"


def _cardio_insight(sessions: list[dict[str, Any]], catalog: dict[str, Any] | None = None) -> str:
    cardio_ids = cardio_part_ids(catalog)
    start_date = today_business_date() - timedelta(days=29)
    daily_minutes = {start_date + timedelta(days=offset): 0 for offset in range(30)}
    for session in sessions:
        day = business_date(session["recorded_at"])
        if day not in daily_minutes:
            continue
        daily_minutes[day] += sum(
            int(exercise.get("duration_minutes") or 0)
            for exercise in session.get("exercises", [])
            if is_cardio_exercise(exercise, cardio_ids)
        )
    total = sum(daily_minutes.values())
    average = total / 30
    absence_days = sum(1 for minutes in daily_minutes.values() if minutes < 10)
    return f"最近30天累计有氧 {total} 分钟，日均 {average:.1f} 分钟，低于10分钟的缺勤天数 {absence_days} 天。"


def _strength_recency_insight(sessions: list[dict[str, Any]], catalog: dict[str, Any] | None = None) -> str | None:
    catalog = catalog or {}
    cardio_ids = cardio_part_ids(catalog)
    part_lookup = {part["id"]: part.get("label", part["id"]) for part in catalog.get("parts", [])}
    strength_part_ids = [
        part["id"]
        for part in catalog.get("parts", [])
        if part.get("active", True) and part["id"] not in cardio_ids
    ]
    if not strength_part_ids:
        return None
    last_by_part: dict[str, date] = {}
    for session in sessions:
        day = business_date(session["recorded_at"])
        for exercise in session.get("exercises", []):
            part_id = exercise.get("part_id")
            if not part_id or part_id in cardio_ids:
                continue
            if part_id not in last_by_part or day > last_by_part[part_id]:
                last_by_part[part_id] = day
    today = today_business_date()
    parts = []
    for part_id in strength_part_ids:
        label = part_lookup.get(part_id, part_id)
        if part_id in last_by_part:
            days = (today - last_by_part[part_id]).days
            parts.append(f"{label}{days}天前")
        else:
            parts.append(f"{label}暂无记录")
    return "距离各部位上一次力量训练：" + "，".join(parts) + "。"


def auto_insights(
    trend_series: dict[str, list[dict[str, Any]]],
    workout_sessions: list[dict[str, Any]] | None = None,
    catalog: dict[str, Any] | None = None,
) -> list[str]:
    insights = []
    weight_series = trend_series.get("weight_kg", [])
    body_fat_series = trend_series.get("body_fat_pct", [])

    weight_insight = _range_change_insight(weight_series, "体重", "kg", "减重", "增重")
    if weight_insight:
        insights.append(weight_insight)

    body_fat_insight = _range_change_insight(body_fat_series, "体脂率", "%", "降低体脂率", "升高体脂率")
    if body_fat_insight:
        insights.append(body_fat_insight)

    fat_mass_insight = _fat_mass_insight(weight_series, body_fat_series)
    if fat_mass_insight:
        insights.append(fat_mass_insight)

    sessions = workout_sessions or []
    insights.append(_cardio_insight(sessions, catalog))

    strength_insight = _strength_recency_insight(sessions, catalog)
    if strength_insight:
        insights.append(strength_insight)

    if not insights:
        insights.append("先录入几条记录，系统会自动生成趋势解读。")
    return insights


def workout_volume_summary(sessions: list[dict[str, Any]], days: int = 30) -> dict[str, Any]:
    cutoff = today_business_date() - timedelta(days=days)
    filtered = [session for session in sessions if business_date(session["recorded_at"]) >= cutoff]
    part_counts: dict[str, int] = defaultdict(int)
    plan_counts: dict[str, int] = defaultdict(int)
    total_sets = 0
    cardio_sessions = 0
    cardio_duration_minutes = 0
    session_details = []
    for session in sorted(filtered, key=lambda item: parse_recorded_at(item["recorded_at"])):
        part_ids = set()
        session_has_cardio = False
        for exercise in session.get("exercises", []):
            part_id = exercise.get("part_id")
            if part_id:
                part_counts[part_id] += 1
                part_ids.add(part_id)
            total_sets += int(exercise.get("sets") or 0)
            if part_id == "cardio":
                session_has_cardio = True
                cardio_duration_minutes += int(exercise.get("duration_minutes") or 0)
        if session_has_cardio:
            cardio_sessions += 1
        if session.get("plan_id"):
            plan_counts[session["plan_id"]] += 1
        session_details.append(
            {
                "recorded_at": session["recorded_at"],
                "exercise_count": len(session.get("exercises", [])),
                "part_ids": sorted(part_ids),
            }
        )
    return {
        "range_days": days,
        "session_count": len(filtered),
        "total_sets": total_sets,
        "part_counts": dict(sorted(part_counts.items(), key=lambda item: (-item[1], item[0]))),
        "plan_counts": dict(sorted(plan_counts.items(), key=lambda item: (-item[1], item[0]))),
        "cardio_sessions": cardio_sessions,
        "cardio_duration_minutes": cardio_duration_minutes,
        "sessions": session_details,
    }
