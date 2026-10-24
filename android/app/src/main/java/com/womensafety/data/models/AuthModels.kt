package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

// User details
data class UserResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("role") val role: String,
    @SerializedName("role_display") val roleDisplay: String?,
    @SerializedName("profile_photo") val profilePhoto: String?,
    @SerializedName("is_verified") val isVerified: Boolean,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    @SerializedName("id_proof_type") val idProofType: String? = null,
    @SerializedName("profile") val profile: UserProfileResponse?
)

// Extended profile details
data class UserProfileResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    @SerializedName("blood_group") val bloodGroup: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("emergency_notes") val emergencyNotes: String?
)

// JWT Token Pair
data class TokenResponse(
    @SerializedName("access") val access: String,
    @SerializedName("refresh") val refresh: String
)

// Auth Response (Login / Register)
data class AuthResponse(
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: UserResponse,
    @SerializedName("tokens") val tokens: TokenResponse
)

// Requests
data class LoginRequest(
    @SerializedName("email_or_phone") val emailOrPhone: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("role") val role: String,
    @SerializedName("password") val password: String,
    @SerializedName("confirm_password") val confirmPassword: String,
    @SerializedName("id_proof_type") val idProofType: String? = null
)

data class SendOtpRequest(
    @SerializedName("phone_or_email") val phoneOrEmail: String,
    @SerializedName("purpose") val purpose: String = "verification"
)

data class VerifyOtpRequest(
    @SerializedName("phone_or_email") val phoneOrEmail: String,
    @SerializedName("otp_code") val otpCode: String,
    @SerializedName("purpose") val purpose: String = "verification"
)

data class GenericResponse(
    @SerializedName("message") val message: String?,
    @SerializedName("detail") val detail: String?,
    @SerializedName("verified") val verified: Boolean?,
    @SerializedName("debug_otp") val debugOtp: String? = null
)
