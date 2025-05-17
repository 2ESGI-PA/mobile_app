package io.businesscare.app.data.model

data class LoginResponse(
    val userId: Int,
    val accessToken: String,
    val role: String
)