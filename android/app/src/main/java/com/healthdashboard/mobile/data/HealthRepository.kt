package com.healthdashboard.mobile.data

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.pow

private const val BusinessDayStartHour = 6L

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

    suspend fun updateWorkoutSession(sessionId: String, payload: WorkoutSessionRequest): WorkoutSessionDto =
        authRepository.service().updateWorkoutSession(sessionId, payload)

    suspend fun deleteWorkoutSession(sessionId: String): WorkoutSessionDto =
        authRepository.service().deleteWorkoutSession(sessionId)

    suspend fun getWidgetMetricOptions(): List<MetricDto> {
        return getMetrics().filter { it.active && it.type == "number" }
    }

    suspend fun getWidgetMetricSummary(metricId: String): WidgetMetricSummary? {
        val now = OffsetDateTime.now(ZoneOffset.ofHours(8))
        val startDate = now.minusHours(BusinessDayStartHour).toLocalDate().minusDays(14)
        val endDate = now.minusHours(BusinessDayStartHour).toLocalDate()
        val profile = runCatching { getProfile() }.getOrNull()
        val metrics = getWidgetMetricOptions()
        val metric = metrics.firstOrNull { it.id == metricId }
        val label = if (metricId == "bmi") "BMI" else metric?.label ?: metricId
        val unit = if (metricId == "bmi") "" else metric?.unit ?: ""
        val entries = getEntries(startDate = startDate.toString(), endDate = endDate.toString())
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

    suspend fun getWorkoutCalendarSummary(days: Long = 42): List<WorkoutCalendarPointDto> {
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val now = OffsetDateTime.now(ZoneOffset.ofHours(8))
        val start = now.minusDays(days)
        val overview = getWorkoutOverview()
        return overview.sessions
            .mapNotNull { session ->
                val recordedAt = runCatching { OffsetDateTime.parse(session.recorded_at, formatter) }.getOrNull() ?: return@mapNotNull null
                val bjTime = recordedAt.withOffsetSameInstant(ZoneOffset.ofHours(8))
                if (bjTime.isBefore(start)) return@mapNotNull null
                val businessDate = bjTime.minusHours(BusinessDayStartHour).toLocalDate()
                val totalDuration = session.exercises.sumOf { it.duration_minutes ?: 0 }
                val totalSets = session.exercises.sumOf { exercise ->
                    if (exercise.part_id == "cardio") 0 else exercise.sets
                }
                WorkoutCalendarPointDto(
                    date = businessDate.toString(),
                    session_count = 1,
                    total_sets = totalSets,
                    total_duration_minutes = totalDuration,
                    parts = session.exercises.map { it.part_id }.distinct(),
                )
            }
            .groupBy { it.date }
            .map { (date, items) ->
                WorkoutCalendarPointDto(
                    date = date,
                    session_count = items.sumOf { it.session_count },
                    total_sets = items.sumOf { it.total_sets },
                    total_duration_minutes = items.sumOf { it.total_duration_minutes },
                    parts = items.flatMap { it.parts }.distinct(),
                )
            }
            .sortedBy { it.date }
    }
}
