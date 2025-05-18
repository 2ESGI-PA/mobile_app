package io.businesscare.app.data.model

data class BookingRequestDto(
    val serviceId: Int?,
    val eventId: Int?,
    val providerId: Int?,
    val bookingDate: String,
    val notes: String?,
    val employeeId: Int
)