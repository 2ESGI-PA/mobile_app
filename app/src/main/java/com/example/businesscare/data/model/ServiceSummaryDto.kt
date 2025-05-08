package com.example.businesscare.data.model

import java.util.Date

data class ServiceSummaryDto(
    val id: Int,
    val title: String,
    val description: String?,
    val price: Double?,
    val realisationTime: String?,
    val providerName: String?,
    val category: String,
    val startDate: Date? = null
) {
    val isEvent: Boolean get() = category.equals("Événement", ignoreCase = true)
    val isService: Boolean get() = category.equals("Service", ignoreCase = true)
}