from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field, field_validator


MetricType = Literal["number", "text"]
ChartType = Literal["line", "bar", "scatter"]
GoalDirection = Literal["up", "down", "neutral"]


class ProfileUpdate(BaseModel):
    height_cm: float = Field(gt=0, lt=300)
    timezone: str = Field(min_length=1, max_length=100)


class LoginRequest(BaseModel):
    password: str = Field(min_length=1, max_length=200)


class MetricBase(BaseModel):
    label: str = Field(min_length=1, max_length=50)
    unit: str = Field(default="", max_length=20)
    type: MetricType
    color: str = Field(min_length=4, max_length=20)
    chart_type: ChartType
    precision: int = Field(ge=0, le=4)
    goal_direction: GoalDirection
    active: bool = True
    show_on_dashboard: bool = True
    sort_order: int = Field(default=100, ge=0, le=9999)


class MetricCreate(MetricBase):
    id: str = Field(min_length=2, max_length=40, pattern=r"^[a-z][a-z0-9_]*$")


class MetricUpdate(MetricBase):
    pass


class EntryBase(BaseModel):
    recorded_at: datetime
    values: dict[str, Any]
    note: str = Field(default="", max_length=500)
    tags: list[str] = Field(default_factory=list)

    @field_validator("tags")
    @classmethod
    def validate_tags(cls, value: list[str]) -> list[str]:
        return [item.strip() for item in value if item.strip()]


class EntryCreate(EntryBase):
    pass


class EntryUpdate(EntryBase):
    pass


class WorkoutPartBase(BaseModel):
    label: str = Field(min_length=1, max_length=50)
    color: str = Field(min_length=4, max_length=20)
    sort_order: int = Field(default=100, ge=0, le=9999)
    active: bool = True


class WorkoutPartCreate(WorkoutPartBase):
    pass


class WorkoutPartUpdate(WorkoutPartBase):
    pass


class WorkoutExerciseBase(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    description: str = Field(default="", max_length=300)
    detail_placeholder: str = Field(default="", max_length=200)
    active: bool = True
    sort_order: int = Field(default=100, ge=0, le=9999)


class WorkoutExerciseCreate(WorkoutExerciseBase):
    pass


class WorkoutExerciseUpdate(WorkoutExerciseBase):
    pass


class WorkoutPlanGroupInput(BaseModel):
    name: str = Field(min_length=1, max_length=60)
    part_id: str = Field(min_length=2, max_length=40, pattern=r"^[a-z][a-z0-9_]*$")
    exercise_ids: list[str] = Field(min_length=1)
    notes: str = Field(default="", max_length=300)
    sort_order: int = Field(default=100, ge=0, le=9999)

    @field_validator("exercise_ids")
    @classmethod
    def validate_exercise_ids(cls, value: list[str]) -> list[str]:
        cleaned = [item.strip() for item in value if item.strip()]
        if not cleaned:
            raise ValueError("exercise_ids cannot be empty")
        return cleaned


class WorkoutPlanBase(BaseModel):
    name: str = Field(min_length=1, max_length=80)
    description: str = Field(default="", max_length=400)
    groups: list[WorkoutPlanGroupInput] = Field(default_factory=list)
    active: bool = True


class WorkoutPlanCreate(WorkoutPlanBase):
    pass


class WorkoutPlanUpdate(WorkoutPlanBase):
    pass


class WorkoutExerciseLogInput(BaseModel):
    part_id: str = Field(min_length=2, max_length=40, pattern=r"^[a-z][a-z0-9_]*$")
    exercise_id: str = Field(min_length=2, max_length=40, pattern=r"^[a-z][a-z0-9_]*$")
    detail: str = Field(default="", max_length=300)
    sets: int = Field(default=1, ge=1, le=50)
    reps: int | None = Field(default=None, ge=1, le=500)
    weight_kg: float | None = Field(default=None, ge=0, le=2000)
    duration_minutes: int | None = Field(default=None, ge=0, le=1440)
    rpe: float | None = Field(default=None, ge=0, le=10)
    note: str = Field(default="", max_length=200)


class WorkoutSessionBase(BaseModel):
    recorded_at: datetime
    plan_id: str | None = Field(default=None, max_length=40)
    exercises: list[WorkoutExerciseLogInput] = Field(default_factory=list)
    note: str = Field(default="", max_length=500)
    tags: list[str] = Field(default_factory=list)
    energy_level: int | None = Field(default=None, ge=1, le=10)

    @field_validator("tags")
    @classmethod
    def validate_workout_tags(cls, value: list[str]) -> list[str]:
        return [item.strip() for item in value if item.strip()]


class WorkoutSessionCreate(WorkoutSessionBase):
    pass


class WorkoutSessionUpdate(WorkoutSessionBase):
    pass
