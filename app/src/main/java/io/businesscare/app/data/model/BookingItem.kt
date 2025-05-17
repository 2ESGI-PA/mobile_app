package io.businesscare.app.data.model

import java.util.Date

data class BookingItem(
    val id: Int,
    val bookingDate: Date,
    val status: String,
    val serviceTitle: String?,
    val itemType: String,
    val location: String?,
    val providerName: String?,
    val notes: String?,
    val createdAt: Date,
    val durationMinutes: Int?,
    val clientName: String?
)