package com.example.businesscare.data.model

data class LoginResponse(
    val userId: Int,
    val accessToken: String,
    val role: String
)