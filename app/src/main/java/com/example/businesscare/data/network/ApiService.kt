package com.example.businesscare.data.network

import com.example.businesscare.data.model.BookingItem
import com.example.businesscare.data.model.BookingRequestDto
import com.example.businesscare.data.model.LoginRequest
import com.example.businesscare.data.model.LoginResponse
import com.example.businesscare.data.model.ServiceSummaryDto
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