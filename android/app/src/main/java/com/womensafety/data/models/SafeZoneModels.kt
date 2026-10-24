package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class SafeZoneResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("user") val user: UserResponse?,
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius_meters") val radiusMeters: Int,
    @SerializedName("notify_on_exit") val notifyOnExit: Boolean,
    @SerializedName("notify_on_entry") val notifyOnEntry: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class CreateSafeZoneRequest(
    @SerializedName("name") val name: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius_meters") val radiusMeters: Int = 200,
    @SerializedName("notify_on_exit") val notifyOnExit: Boolean = true,
    @SerializedName("notify_on_entry") val notifyOnEntry: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class CheckGeofenceRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double
)

data class ZoneCheckResult(
    @SerializedName("zone_id") val zoneId: Int,
    @SerializedName("zone_name") val zoneName: String,
    @SerializedName("radius_meters") val radiusMeters: Int,
    @SerializedName("current_distance_meters") val currentDistanceMeters: Double,
    @SerializedName("is_inside") val isInside: Boolean
)

data class CheckGeofenceResponse(
    @SerializedName("is_inside_safe_zone") val isInsideSafeZone: Boolean,
    @SerializedName("zones") val zones: List<ZoneCheckResult>
)

data class SafeZoneEventResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("safe_zone") val safeZoneId: Int,
    @SerializedName("safe_zone_name") val safeZoneName: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_type_display") val eventTypeDisplay: String?,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("distance_meters") val distanceMeters: Double,
    @SerializedName("notified_guardians") val notifiedGuardians: Boolean,
    @SerializedName("timestamp") val timestamp: String
)
