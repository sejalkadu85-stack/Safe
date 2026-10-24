package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class LocationLogResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("user") val user: UserResponse?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Double?,
    @SerializedName("speed") val speed: Double?,
    @SerializedName("altitude") val altitude: Double?,
    @SerializedName("heading") val heading: Double?,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("is_mock") val isMock: Boolean,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("google_maps_url") val googleMapsUrl: String?
)

data class LocationUpdateRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Double? = null,
    @SerializedName("speed") val speed: Double? = null,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("heading") val heading: Double? = null,
    @SerializedName("battery_level") val batteryLevel: Int? = null,
    @SerializedName("is_mock") val isMock: Boolean = false
)

data class LocationShareSessionResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("user") val user: UserResponse?,
    @SerializedName("shared_with") val sharedWith: List<UserResponse>?,
    @SerializedName("share_with_all_trusted") val shareWithAllTrusted: Boolean,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_currently_active") val isCurrentlyActive: Boolean,
    @SerializedName("note") val note: String?
)

data class StartLocationShareRequest(
    @SerializedName("duration_minutes") val durationMinutes: Int = 60,
    @SerializedName("note") val note: String = "",
    @SerializedName("share_with_all_trusted") val shareWithAllTrusted: Boolean = true,
    @SerializedName("shared_with_ids") val sharedWithIds: List<Int> = emptyList()
)

data class LiveLocationResponse(
    @SerializedName("user") val user: UserResponse,
    @SerializedName("latest_location") val latestLocation: LocationLogResponse?,
    @SerializedName("has_active_sos") val hasActiveSos: Boolean,
    @SerializedName("active_sos") val activeSos: SOSAlertResponse?,
    @SerializedName("is_actively_sharing") val isActivelySharing: Boolean,
    @SerializedName("share_session") val shareSession: LocationShareSessionResponse?
)

data class LocationShareStatusResponse(
    @SerializedName("is_actively_sharing") val isActivelySharing: Boolean,
    @SerializedName("session") val session: LocationShareSessionResponse?
)
