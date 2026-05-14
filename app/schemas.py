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
