package io.businesscare.app.data.model

data class BookingRequestDto(
    val serviceId: Int?,
    val eventId: Int?,
    val bookingDate: String, // ISO 8601 UTC String
    val notes: String?
)