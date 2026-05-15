package com.healthdashboard.mobile.data

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.pow

class HealthRepository(private val authRepository: AuthRepository) {
    suspend fun getMetrics(): List<MetricDto> = authRepository.service().getMetrics()

    suspend fun getProfile(): ProfileDto = authRepository.service().getProfile()

    suspend fun getEntries(
        startDate: String? = null,
        endDate: String? = null,
        query: String? = null,
    ): List<EntryDto> = authRepository.service().getEntries(startDate, endDate, query)

    suspend fun createEntry(payload: EntryRequest): EntryDto =
        authRepository.service().createEntry(payload)

    suspend fun updateEntry(entryId: String, payload: EntryRequest): EntryDto =
        authRepository.service().updateEntry(entryId, payload)

    suspend fun deleteEntry(entryId: String): EntryDto =
        authRepository.service().deleteEntry(entryId)

    suspend fun getDashboard(range: String): DashboardDto =
        authRepository.service().getDashboard(range)

    suspend fun getWorkoutOverview(): WorkoutOverviewDto =
        authRepository.service().getWorkoutOverview()

    suspend fun createWorkoutSession(payload: WorkoutSessionRequest): WorkoutSessionDto =
        authRepository.service().createWorkoutSession(payload)

    suspend fun getWidgetMetricOptions(): List<MetricDto> {
        return getMetrics().filter { it.active && it.type == "number" }
    }

    suspend fun getWidgetMetricSummary(metricId: String): WidgetMetricSummary? {
        val now = OffsetDateTime.now(ZoneOffset.ofHours(8))
        val start = now.minusDays(14)
        val profile = runCatching { getProfile() }.getOrNull()
        val metrics = getWidgetMetricOptions()
        val metric = metrics.firstOrNull { it.id == metricId }
        val label = if (metricId == "bmi") "BMI" else metric?.label ?: metricId
        val unit = if (metricId == "bmi") "" else metric?.unit ?: ""
        val entries = getEntries(startDate = start.toLocalDate().toString(), endDate = now.toLocalDate().toString())
        val points = entries
            .mapNotNull { entry ->
                val value = when (metricId) {
                    "bmi" -> {
                        val weight = (entry.values["weight_kg"] as? Number)?.toDouble()
                        val heightCm = profile?.height_cm
                        if (weight != null && heightCm != null && heightCm > 0) {
                            weight / (heightCm / 100.0).pow(2)
                        } else {
                            null
                        }
                    }

                    else -> (entry.values[metricId] as? Number)?.toDouble()
                }
                if (value == null) null else WidgetMetricPoint(entry.recorded_at, value)
            }
            .sortedBy { it.recordedAt }
        return WidgetMetricSummary(
            metricId = metricId,
            label = label,
            unit = unit,
            latest = points.lastOrNull()?.value,
            points = points.takeLast(14),
        )
    }
}
