package com.healthdashboard.mobile.data

class HealthRepository(private val authRepository: AuthRepository) {
    suspend fun getMetrics(): List<MetricDto> = authRepository.service().getMetrics()

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
}
