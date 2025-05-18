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

    @GET("employees/schedule")
    suspend fun getSchedule(): List<BookingItem>

    @GET("employees/services")
    suspend fun getAvailableServices(): List<ServiceSummaryDto>

    @POST("employees/booking/create")
    suspend fun createBooking(@Body bookingRequest: BookingRequestDto): Response<Void>

    @DELETE("employees/bookings/{id}")
    suspend fun cancelBooking(@Path("id") bookingId: Int): Response<Void>
}