package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class VolunteerUser(
    val id: Int,
    val username: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    val email: String?
)

data class Volunteer(
    val id: Int,
    val user: VolunteerUser?,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("is_available") val isAvailable: Boolean = true,
    @SerializedName("phone_number") val phoneNumber: String?,
    val skills: String?,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    @SerializedName("total_helps") val totalHelps: Int = 0,
    val rating: Double = 5.0,
    @SerializedName("rating_count") val ratingCount: Int = 0,
    @SerializedName("distance_meters") val distanceMeters: Double? = null
)

data class VolunteerListResponse(
    val success: Boolean,
    val count: Int,
    @SerializedName("radius_meters") val radiusMeters: Double?,
    val volunteers: List<Volunteer>
)

data class VolunteerProfileResponse(
    val success: Boolean,
    @SerializedName("is_registered") val isRegistered: Boolean = false,
    val message: String?,
    val volunteer: Volunteer?
)

data class VolunteerRequestItem(
    val id: Int,
    val requester: VolunteerUser?,
    val volunteer: Volunteer?,
    val status: String,
    @SerializedName("emergency_type") val emergencyType: String,
    val message: String?,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val rating: Int?,
    val feedback: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class VolunteerRequestsResponse(
    val success: Boolean,
    val count: Int,
    val requests: List<VolunteerRequestItem>
)

data class VolunteerRequestCreateResponse(
    val success: Boolean,
    val message: String,
    val request: VolunteerRequestItem?
)

data class Helpline(
    val id: Int,
    val name: String,
    val number: String,
    val category: String,
    val description: String?,
    val city: String?,
    val state: String?,
    val country: String? = "India",
    @SerializedName("is_national") val isNational: Boolean = true,
    @SerializedName("is_toll_free") val isTollFree: Boolean = true,
    val priority: Int = 10
)

data class HelplineResponse(
    val success: Boolean,
    val count: Int,
    val helplines: List<Helpline>
)
