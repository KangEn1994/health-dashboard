package com.healthdashboard.mobile.data

data class LoginRequest(val password: String)

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
)

data class MetricDto(
    val id: String,
    val label: String,
    val unit: String,
    val type: String,
    val color: String,
    val chart_type: String,
    val precision: Int,
    val goal_direction: String,
    val active: Boolean,
    val show_on_dashboard: Boolean,
    val sort_order: Int,
)

data class EntryDto(
    val id: String,
    val recorded_at: String,
    val values: Map<String, Any?>,
    val note: String,
    val tags: List<String>,
    val created_at: String,
    val updated_at: String,
)

data class EntryRequest(
    val recorded_at: String,
    val values: Map<String, Any?>,
    val note: String,
    val tags: List<String>,
)

data class SummaryDto(
    val latest: Double?,
    val delta: Double?,
    val average_7d: Double?,
    val average_30d: Double?,
    val min: Double?,
    val max: Double?,
)

data class TrendPointDto(
    val recorded_at: String,
    val value: Double,
)

data class DashboardDto(
    val range: String,
    val metrics: List<MetricDto>,
    val summaries: Map<String, SummaryDto>,
    val trends: Map<String, List<TrendPointDto>>,
    val insights: List<String>,
)

data class WorkoutCalendarPointDto(
    val date: String,
    val session_count: Int,
    val total_sets: Int,
    val total_duration_minutes: Int,
    val parts: List<String>,
)

data class ProfileDto(
    val height_cm: Double,
    val timezone: String,
)

data class WorkoutPartDto(
    val id: String,
    val label: String,
    val color: String,
    val sort_order: Int,
    val active: Boolean,
)

data class WorkoutExerciseDto(
    val id: String,
    val name: String,
    val description: String,
    val detail_placeholder: String,
    val active: Boolean,
    val sort_order: Int,
)

data class WorkoutCatalogDto(
    val parts: List<WorkoutPartDto>,
    val exercises: Map<String, List<WorkoutExerciseDto>>,
)

data class WorkoutPlanGroupDto(
    val id: String,
    val name: String,
    val part_id: String,
    val exercise_ids: List<String>,
    val notes: String,
    val sort_order: Int,
)

data class WorkoutPlanDto(
    val id: String,
    val name: String,
    val description: String,
    val groups: List<WorkoutPlanGroupDto>,
    val active: Boolean,
)

data class WorkoutSessionExerciseDto(
    val part_id: String,
    val exercise_id: String,
    val detail: String,
    val sets: Int,
    val reps: Int?,
    val weight_kg: Double?,
    val duration_minutes: Int?,
    val rpe: Double?,
    val note: String,
)

data class WorkoutSessionDto(
    val id: String,
    val recorded_at: String,
    val plan_id: String?,
    val exercises: List<WorkoutSessionExerciseDto>,
    val note: String,
    val tags: List<String>,
    val energy_level: Int?,
    val created_at: String,
    val updated_at: String,
)

data class WorkoutSummaryDto(
    val range_days: Int,
    val session_count: Int,
    val total_sets: Int,
    val part_counts: Map<String, Int>,
    val plan_counts: Map<String, Int>,
    val cardio_sessions: Int,
    val cardio_duration_minutes: Int,
)

data class WorkoutOverviewDto(
    val catalog: WorkoutCatalogDto,
    val plans: List<WorkoutPlanDto>,
    val sessions: List<WorkoutSessionDto>,
    val summary_14d: WorkoutSummaryDto,
    val summary_30d: WorkoutSummaryDto,
    val recommendations: List<String>,
)

data class WorkoutExerciseRequest(
    val part_id: String,
    val exercise_id: String,
    val detail: String,
    val sets: Int,
    val reps: Int?,
    val weight_kg: Double?,
    val duration_minutes: Int?,
    val rpe: Double?,
    val note: String = "",
)

data class WorkoutSessionRequest(
    val recorded_at: String,
    val plan_id: String?,
    val exercises: List<WorkoutExerciseRequest>,
    val note: String,
    val tags: List<String>,
    val energy_level: Int?,
)
