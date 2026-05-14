package com.healthdashboard.mobile.data

data class WidgetMetricPoint(
    val recordedAt: String,
    val value: Double,
)

data class WidgetMetricSummary(
    val metricId: String,
    val label: String,
    val unit: String,
    val latest: Double?,
    val points: List<WidgetMetricPoint>,
)
