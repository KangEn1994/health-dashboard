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
