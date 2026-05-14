package com.healthdashboard.mobile.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body payload: LoginRequest): LoginResponse

    @GET("api/metrics")
    suspend fun getMetrics(): List<MetricDto>

    @GET("api/profile")
    suspend fun getProfile(): ProfileDto

    @GET("api/entries")
    suspend fun getEntries(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("query") query: String? = null,
    ): List<EntryDto>

    @POST("api/entries")
    suspend fun createEntry(@Body payload: EntryRequest): EntryDto

    @PUT("api/entries/{entryId}")
    suspend fun updateEntry(
        @Path("entryId") entryId: String,
        @Body payload: EntryRequest,
    ): EntryDto

    @DELETE("api/entries/{entryId}")
    suspend fun deleteEntry(@Path("entryId") entryId: String): EntryDto

    @GET("api/dashboard")
    suspend fun getDashboard(@Query("range") range: String): DashboardDto
}
