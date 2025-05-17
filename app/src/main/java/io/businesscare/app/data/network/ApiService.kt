package io.businesscare.app.data.network

import io.businesscare.app.data.model.BookingItem
import io.businesscare.app.data.model.BookingRequestDto
import io.businesscare.app.data.model.LoginRequest
import io.businesscare.app.data.model.LoginResponse
import io.businesscare.app.data.model.ServiceSummaryDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse

    @GET("employee/me/schedule")
    suspend fun getSchedule(): List<BookingItem>

    @GET("employee/me/services")
    suspend fun getAvailableServices(): List<ServiceSummaryDto>

    @POST("employee/me/bookings")
    suspend fun createBooking(@Body bookingRequest: BookingRequestDto): BookingItem

    @DELETE("employee/me/bookings/{bookingId}")
    suspend fun cancelBooking(@Path("bookingId") bookingId: Int): Response<Unit>
}