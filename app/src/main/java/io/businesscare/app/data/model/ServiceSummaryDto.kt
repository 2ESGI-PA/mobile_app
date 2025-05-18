package io.businesscare.app.data.model

import java.util.Date

data class ServiceSummaryDto(
    val id: Int,
    val title: String,
    val description: String?,
    val price: String?,
    val realisationTime: String?,
    val isMedical: Boolean?,
    val isAvailable: Boolean?,
    val isNegotiable: Boolean?,
    val isEvent: Boolean = false,
    val category: String? = null,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val location: String? = null,
    val maxAttendees: Int? = null,
    val provider: ProviderNestedDto?
)