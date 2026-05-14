from __future__ import annotations

from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from fastapi import HTTPException

from app import analytics
from app.repository import JsonStore
from app.schemas import EntryCreate, EntryUpdate, MetricCreate, MetricUpdate, ProfileUpdate


class DashboardService:
    def __init__(self, store: JsonStore) -> None:
        self.store = store

    def get_profile(self) -> dict[str, Any]:
        return self.store.get_profile()

    def update_profile(self, payload: ProfileUpdate) -> dict[str, Any]:
        return self.store.update_profile(payload.model_dump())

    def get_metrics(self) -> list[dict[str, Any]]:
        return sorted(self.store.get_metrics(), key=lambda item: (item["sort_order"], item["label"]))

    def create_metric(self, payload: MetricCreate) -> dict[str, Any]:
        metrics = self.store.get_metrics()
        if any(metric["id"] == payload.id for metric in metrics):
            raise HTTPException(status_code=422, detail="metric_id already exists")
        metric = payload.model_dump()
        metrics.append(metric)
        self.store.save_metrics(metrics)
        return metric

    def update_metric(self, metric_id: str, payload: MetricUpdate) -> dict[str, Any]:
        metrics = self.store.get_metrics()
        for index, metric in enumerate(metrics):
            if metric["id"] == metric_id:
                updated = {"id": metric_id, **payload.model_dump()}
                metrics[index] = updated
                self.store.save_metrics(metrics)
                return updated
        raise HTTPException(status_code=404, detail="metric not found")

    def archive_metric(self, metric_id: str) -> dict[str, Any]:
        metrics = self.store.get_metrics()
        for index, metric in enumerate(metrics):
            if metric["id"] == metric_id:
                metric["active"] = False
                metrics[index] = metric
                self.store.save_metrics(metrics)
                return metric
        raise HTTPException(status_code=404, detail="metric not found")

    def get_entries(
        self,
        start_date: str | None = None,
        end_date: str | None = None,
        query: str | None = None,
    ) -> list[dict[str, Any]]:
        entries = self.store.get_entries()
        filtered = []
        for entry in entries:
            recorded_at = analytics.parse_recorded_at_beijing(entry["recorded_at"]).date().isoformat()
            if start_date and recorded_at < start_date:
                continue
            if end_date and recorded_at > end_date:
                continue
            if query:
                haystack = f"{entry.get('note', '')} {' '.join(entry.get('tags', []))}".lower()
                if query.lower() not in haystack:
                    continue
            filtered.append(entry)
        return list(reversed(analytics.sort_entries(filtered)))

    def _metric_map(self) -> dict[str, dict[str, Any]]:
        metrics = self.store.get_metrics()
        return {metric["id"]: metric for metric in metrics}

    def _normalize_values(self, values: dict[str, Any]) -> dict[str, Any]:
        normalized: dict[str, Any] = {}
        seen: set[str] = set()
        metric_map = self._metric_map()
        for metric_id, value in values.items():
            if metric_id in seen:
                raise HTTPException(status_code=422, detail=f"duplicate metric id: {metric_id}")
            seen.add(metric_id)
            metric = metric_map.get(metric_id)
            if not metric:
                raise HTTPException(status_code=422, detail=f"undefined metric id: {metric_id}")
            if metric["type"] == "number":
                try:
                    number = round(float(value), metric["precision"])
                except (TypeError, ValueError) as exc:
                    raise HTTPException(status_code=422, detail=f"invalid number for {metric_id}") from exc
                normalized[metric_id] = int(number) if metric["precision"] == 0 else number
            else:
                normalized[metric_id] = str(value).strip()
        return normalized

    def create_entry(self, payload: EntryCreate) -> dict[str, Any]:
        entries = self.store.get_entries()
        now = analytics.now_beijing().isoformat()
        entry = {
            "id": uuid4().hex,
            "recorded_at": payload.recorded_at.astimezone(analytics.BEIJING_TZ).isoformat(),
            "values": self._normalize_values(payload.values),
            "note": payload.note.strip(),
            "tags": payload.tags,
            "created_at": now,
            "updated_at": now,
        }
        entries.append(entry)
        self.store.save_entries(entries)
        return entry

    def update_entry(self, entry_id: str, payload: EntryUpdate) -> dict[str, Any]:
        entries = self.store.get_entries()
        for index, entry in enumerate(entries):
            if entry["id"] == entry_id:
                updated = {
                    "id": entry["id"],
                    "recorded_at": payload.recorded_at.astimezone(analytics.BEIJING_TZ).isoformat(),
                    "values": self._normalize_values(payload.values),
                    "note": payload.note.strip(),
                    "tags": payload.tags,
                    "created_at": entry["created_at"],
                    "updated_at": analytics.now_beijing().isoformat(),
                }
                entries[index] = updated
                self.store.save_entries(entries)
                return updated
        raise HTTPException(status_code=404, detail="entry not found")

    def delete_entry(self, entry_id: str) -> dict[str, Any]:
        entries = self.store.get_entries()
        for index, entry in enumerate(entries):
            if entry["id"] == entry_id:
                removed = entries.pop(index)
                self.store.save_entries(entries)
                return removed
        raise HTTPException(status_code=404, detail="entry not found")

    def dashboard(self, range_name: str) -> dict[str, Any]:
        try:
            days = analytics.parse_range(range_name)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        profile = self.store.get_profile()
        metrics = self.get_metrics()
        active_numeric = [metric for metric in metrics if metric["type"] == "number" and metric["active"]]
        entries = self.store.get_entries()
        filtered = analytics.filter_entries_by_days(entries, days)
        metric_summaries = {}
        trend_series = {}
        for metric in active_numeric:
            series = analytics.series_for_metric(filtered, metric["id"], profile["height_cm"])
            metric_summaries[metric["id"]] = analytics.summary_for_series(series)
            trend_series[metric["id"]] = series
        bmi_series = analytics.series_for_metric(filtered, "bmi", profile["height_cm"])
        metric_summaries["bmi"] = analytics.summary_for_series(bmi_series)
        trend_series["bmi"] = bmi_series
        weight_series = trend_series.get("weight_kg", [])
        weight_velocity = None
        if len(weight_series) >= 2:
            first = analytics.parse_recorded_at(weight_series[0]["recorded_at"])
            last = analytics.parse_recorded_at(weight_series[-1]["recorded_at"])
            span_days = max((last - first).days, 1)
            weight_velocity = round((weight_series[-1]["value"] - weight_series[0]["value"]) / span_days * 7, 3)
        compare_days = days if days is not None else 30
        comparisons = {
            metric["id"]: analytics.period_average_comparison(
                analytics.series_for_metric(entries, metric["id"], profile["height_cm"]),
                compare_days,
            )
            for metric in active_numeric
        }
        correlations = analytics.correlation_pairs(
            filtered,
            [metric["id"] for metric in active_numeric] + ["bmi"],
            profile["height_cm"],
        )
        return {
            "profile": profile,
            "range": range_name,
            "metrics": metrics,
            "summaries": metric_summaries,
            "trends": trend_series,
            "comparisons": comparisons,
            "continuity": analytics.continuity_stats(entries),
            "weight_velocity_per_week": weight_velocity,
            "correlations": correlations,
            "calendar_heatmap": analytics.calendar_heatmap(entries),
            "insights": analytics.auto_insights(metric_summaries, trend_series),
            "entry_count": len(entries),
        }

    def analytics(self, range_name: str, metric_ids: list[str]) -> dict[str, Any]:
        try:
            days = analytics.parse_range(range_name)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        profile = self.store.get_profile()
        metrics = self._metric_map()
        entries = self.store.get_entries()
        filtered_entries = analytics.filter_entries_by_days(entries, days)
        result = {}
        for metric_id in metric_ids:
            if metric_id != "bmi" and metric_id not in metrics:
                raise HTTPException(status_code=422, detail=f"undefined metric id: {metric_id}")
            series = analytics.series_for_metric(filtered_entries, metric_id, profile["height_cm"])
            full_series = analytics.series_for_metric(entries, metric_id, profile["height_cm"])
            weekly = analytics.weekly_changes(series)
            weekly_changes = [item["change"] for item in weekly]
            result[metric_id] = {
                "series": series,
                "moving_average_7": analytics.moving_average(series, 7),
                "weekly_average_comparison": analytics.period_average_comparison(full_series, 7),
                "monthly_average_comparison": analytics.period_average_comparison(full_series, 30),
                "period_comparison": analytics.period_average_comparison(full_series, days or 30),
                "plateau": analytics.plateau_detection(series, 14, 0.05),
                "best_week_change": max(weekly_changes) if weekly_changes else None,
                "worst_week_change": min(weekly_changes) if weekly_changes else None,
            }
        return {"range": range_name, "metrics": result}
