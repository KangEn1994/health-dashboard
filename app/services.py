from __future__ import annotations

from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from fastapi import HTTPException

from app import analytics
from app.repository import JsonStore
from app.schemas import EntryCreate, EntryUpdate, MetricCreate, MetricUpdate, ProfileUpdate
from app.schemas import (
    WorkoutExerciseCreate,
    WorkoutExerciseUpdate,
    WorkoutPartCreate,
    WorkoutPartUpdate,
    WorkoutPlanCreate,
    WorkoutPlanUpdate,
    WorkoutSessionCreate,
    WorkoutSessionUpdate,
)


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
        workout_sessions = self.store.get_workout_sessions()
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
        workout_duration_series = analytics.workout_duration_series(workout_sessions, days)
        metric_summaries["workout_duration_min"] = analytics.summary_for_series(workout_duration_series)
        trend_series["workout_duration_min"] = workout_duration_series
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
            ["weight_kg", "body_fat_pct", "bmi", "workout_duration_min"],
            profile["height_cm"],
            {
                analytics.parse_recorded_at_beijing(point["recorded_at"]).date().isoformat(): point["value"]
                for point in workout_duration_series
            },
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

    def get_workout_catalog(self) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        catalog["parts"] = sorted(catalog.get("parts", []), key=lambda item: (item["sort_order"], item["label"]))
        for part_id, exercises in list(catalog.get("exercises", {}).items()):
            catalog["exercises"][part_id] = sorted(exercises, key=lambda item: (item["sort_order"], item["name"]))
        return catalog

    def _save_workout_catalog(self, catalog: dict[str, Any]) -> dict[str, Any]:
        return self.store.save_workout_catalog(catalog)

    def _find_part(self, catalog: dict[str, Any], part_id: str) -> dict[str, Any] | None:
        return next((part for part in catalog.get("parts", []) if part["id"] == part_id), None)

    def create_workout_part(self, part_id: str, payload: WorkoutPartCreate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        if self._find_part(catalog, part_id):
            raise HTTPException(status_code=422, detail="workout_part_id already exists")
        part = {"id": part_id, **payload.model_dump()}
        catalog.setdefault("parts", []).append(part)
        catalog.setdefault("exercises", {})[part_id] = catalog.get("exercises", {}).get(part_id, [])
        self._save_workout_catalog(catalog)
        return part

    def update_workout_part(self, part_id: str, payload: WorkoutPartUpdate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        for index, part in enumerate(catalog.get("parts", [])):
            if part["id"] == part_id:
                updated = {"id": part_id, **payload.model_dump()}
                catalog["parts"][index] = updated
                self._save_workout_catalog(catalog)
                return updated
        raise HTTPException(status_code=404, detail="workout part not found")

    def delete_workout_part(self, part_id: str) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        plans = self.store.get_workout_plans()
        sessions = self.store.get_workout_sessions()

        for index, part in enumerate(catalog.get("parts", [])):
            if part["id"] != part_id:
                continue
            if any(group.get("part_id") == part_id for plan in plans for group in plan.get("groups", [])):
                raise HTTPException(status_code=422, detail="workout part is used by plan groups")
            if any(exercise.get("part_id") == part_id for session in sessions for exercise in session.get("exercises", [])):
                raise HTTPException(status_code=422, detail="workout part is used by workout sessions")
            removed = catalog["parts"].pop(index)
            catalog.get("exercises", {}).pop(part_id, None)
            self._save_workout_catalog(catalog)
            return removed
        raise HTTPException(status_code=404, detail="workout part not found")

    def create_workout_exercise(self, part_id: str, exercise_id: str, payload: WorkoutExerciseCreate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        if not self._find_part(catalog, part_id):
            raise HTTPException(status_code=422, detail="workout part not found")
        exercises = catalog.setdefault("exercises", {}).setdefault(part_id, [])
        if any(item["id"] == exercise_id for item in exercises):
            raise HTTPException(status_code=422, detail="exercise_id already exists")
        exercise = {"id": exercise_id, **payload.model_dump()}
        exercises.append(exercise)
        self._save_workout_catalog(catalog)
        return exercise

    def update_workout_exercise(self, part_id: str, exercise_id: str, payload: WorkoutExerciseUpdate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        exercises = catalog.setdefault("exercises", {}).setdefault(part_id, [])
        for index, exercise in enumerate(exercises):
            if exercise["id"] == exercise_id:
                updated = {"id": exercise_id, **payload.model_dump()}
                exercises[index] = updated
                self._save_workout_catalog(catalog)
                return updated
        raise HTTPException(status_code=404, detail="workout exercise not found")

    def delete_workout_exercise(self, part_id: str, exercise_id: str) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        plans = self.store.get_workout_plans()
        sessions = self.store.get_workout_sessions()
        exercises = catalog.setdefault("exercises", {}).setdefault(part_id, [])

        for index, exercise in enumerate(exercises):
            if exercise["id"] != exercise_id:
                continue
            if any(
                group.get("part_id") == part_id and exercise_id in group.get("exercise_ids", [])
                for plan in plans
                for group in plan.get("groups", [])
            ):
                raise HTTPException(status_code=422, detail="workout exercise is used by plan groups")
            if any(
                logged.get("part_id") == part_id and logged.get("exercise_id") == exercise_id
                for session in sessions
                for logged in session.get("exercises", [])
            ):
                raise HTTPException(status_code=422, detail="workout exercise is used by workout sessions")
            removed = exercises.pop(index)
            self._save_workout_catalog(catalog)
            return removed
        raise HTTPException(status_code=404, detail="workout exercise not found")

    def get_workout_plans(self) -> list[dict[str, Any]]:
        return sorted(self.store.get_workout_plans(), key=lambda item: item["name"])

    def _validate_plan_groups(self, groups: list[dict[str, Any]], catalog: dict[str, Any]) -> None:
        part_ids = {part["id"] for part in catalog.get("parts", [])}
        exercise_map = {
            part_id: {exercise["id"] for exercise in exercises}
            for part_id, exercises in catalog.get("exercises", {}).items()
        }
        for group in groups:
            if group["part_id"] not in part_ids:
                raise HTTPException(status_code=422, detail=f"undefined workout part: {group['part_id']}")
            missing = [exercise_id for exercise_id in group["exercise_ids"] if exercise_id not in exercise_map.get(group["part_id"], set())]
            if missing:
                raise HTTPException(status_code=422, detail=f"undefined exercises for part {group['part_id']}: {', '.join(missing)}")

    def create_workout_plan(self, payload: WorkoutPlanCreate) -> dict[str, Any]:
        plans = self.store.get_workout_plans()
        plan_id = uuid4().hex
        catalog = self.store.get_workout_catalog()
        groups = []
        for group in payload.groups:
            item = group.model_dump()
            item["id"] = uuid4().hex
            groups.append(item)
        self._validate_plan_groups(groups, catalog)
        plan = {
            "id": plan_id,
            "name": payload.name.strip(),
            "description": payload.description.strip(),
            "groups": groups,
            "active": payload.active,
        }
        plans.append(plan)
        self.store.save_workout_plans(plans)
        return plan

    def update_workout_plan(self, plan_id: str, payload: WorkoutPlanUpdate) -> dict[str, Any]:
        plans = self.store.get_workout_plans()
        catalog = self.store.get_workout_catalog()
        for index, plan in enumerate(plans):
            if plan["id"] == plan_id:
                groups = []
                for existing_group, incoming_group in zip(plan.get("groups", []), payload.groups, strict=False):
                    group = incoming_group.model_dump()
                    group["id"] = existing_group.get("id", uuid4().hex)
                    groups.append(group)
                if len(payload.groups) > len(plan.get("groups", [])):
                    for extra_group in payload.groups[len(plan.get("groups", [])) :]:
                        group = extra_group.model_dump()
                        group["id"] = uuid4().hex
                        groups.append(group)
                self._validate_plan_groups(groups, catalog)
                updated = {
                    "id": plan_id,
                    "name": payload.name.strip(),
                    "description": payload.description.strip(),
                    "groups": groups,
                    "active": payload.active,
                }
                plans[index] = updated
                self.store.save_workout_plans(plans)
                return updated
        raise HTTPException(status_code=404, detail="workout plan not found")

    def delete_workout_plan(self, plan_id: str) -> dict[str, Any]:
        plans = self.store.get_workout_plans()
        for index, plan in enumerate(plans):
            if plan["id"] == plan_id:
                removed = plans.pop(index)
                self.store.save_workout_plans(plans)
                return removed
        raise HTTPException(status_code=404, detail="workout plan not found")

    def _validate_workout_exercises(self, exercises: list[dict[str, Any]], catalog: dict[str, Any]) -> list[dict[str, Any]]:
        parts = {part["id"] for part in catalog.get("parts", [])}
        exercise_lookup = {
            part_id: {exercise["id"]: exercise for exercise in items}
            for part_id, items in catalog.get("exercises", {}).items()
        }
        normalized = []
        for item in exercises:
            part_id = item["part_id"]
            exercise_id = item["exercise_id"]
            if part_id not in parts:
                raise HTTPException(status_code=422, detail=f"undefined workout part: {part_id}")
            if exercise_id not in exercise_lookup.get(part_id, {}):
                raise HTTPException(status_code=422, detail=f"undefined workout exercise: {exercise_id}")
            normalized.append(
                {
                    **item,
                    "detail": item.get("detail", "").strip(),
                    "note": item.get("note", "").strip(),
                }
            )
        return normalized

    def get_workout_sessions(
        self,
        start_date: str | None = None,
        end_date: str | None = None,
        query: str | None = None,
    ) -> list[dict[str, Any]]:
        sessions = self.store.get_workout_sessions()
        filtered = []
        for session in sessions:
            recorded_at = analytics.parse_recorded_at_beijing(session["recorded_at"]).date().isoformat()
            if start_date and recorded_at < start_date:
                continue
            if end_date and recorded_at > end_date:
                continue
            if query:
                details = " ".join(
                    [
                        session.get("note", ""),
                        " ".join(session.get("tags", [])),
                        " ".join(exercise.get("detail", "") for exercise in session.get("exercises", [])),
                        " ".join(exercise.get("note", "") for exercise in session.get("exercises", [])),
                    ]
                ).lower()
                if query.lower() not in details:
                    continue
            filtered.append(session)
        return sorted(filtered, key=lambda item: analytics.parse_recorded_at(item["recorded_at"]), reverse=True)

    def create_workout_session(self, payload: WorkoutSessionCreate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        sessions = self.store.get_workout_sessions()
        plans = self.store.get_workout_plans()
        if payload.plan_id and not any(plan["id"] == payload.plan_id for plan in plans):
            raise HTTPException(status_code=422, detail="undefined workout plan")
        now = analytics.now_beijing().isoformat()
        session = {
            "id": uuid4().hex,
            "recorded_at": payload.recorded_at.astimezone(analytics.BEIJING_TZ).isoformat(),
            "plan_id": payload.plan_id,
            "exercises": self._validate_workout_exercises([item.model_dump() for item in payload.exercises], catalog),
            "note": payload.note.strip(),
            "tags": payload.tags,
            "energy_level": payload.energy_level,
            "created_at": now,
            "updated_at": now,
        }
        sessions.append(session)
        self.store.save_workout_sessions(sessions)
        return session

    def update_workout_session(self, session_id: str, payload: WorkoutSessionUpdate) -> dict[str, Any]:
        catalog = self.store.get_workout_catalog()
        sessions = self.store.get_workout_sessions()
        plans = self.store.get_workout_plans()
        if payload.plan_id and not any(plan["id"] == payload.plan_id for plan in plans):
            raise HTTPException(status_code=422, detail="undefined workout plan")
        for index, session in enumerate(sessions):
            if session["id"] == session_id:
                updated = {
                    "id": session_id,
                    "recorded_at": payload.recorded_at.astimezone(analytics.BEIJING_TZ).isoformat(),
                    "plan_id": payload.plan_id,
                    "exercises": self._validate_workout_exercises([item.model_dump() for item in payload.exercises], catalog),
                    "note": payload.note.strip(),
                    "tags": payload.tags,
                    "energy_level": payload.energy_level,
                    "created_at": session["created_at"],
                    "updated_at": analytics.now_beijing().isoformat(),
                }
                sessions[index] = updated
                self.store.save_workout_sessions(sessions)
                return updated
        raise HTTPException(status_code=404, detail="workout session not found")

    def delete_workout_session(self, session_id: str) -> dict[str, Any]:
        sessions = self.store.get_workout_sessions()
        for index, session in enumerate(sessions):
            if session["id"] == session_id:
                removed = sessions.pop(index)
                self.store.save_workout_sessions(sessions)
                return removed
        raise HTTPException(status_code=404, detail="workout session not found")

    def workout_overview(self) -> dict[str, Any]:
        catalog = self.get_workout_catalog()
        plans = self.get_workout_plans()
        sessions = self.store.get_workout_sessions()
        return {
            "catalog": catalog,
            "plans": plans,
            "sessions": self.get_workout_sessions(),
            "summary_14d": analytics.workout_volume_summary(sessions, 14),
            "summary_30d": analytics.workout_volume_summary(sessions, 30),
            "recommendations": analytics.workout_recommendations(catalog, plans, sessions),
        }
