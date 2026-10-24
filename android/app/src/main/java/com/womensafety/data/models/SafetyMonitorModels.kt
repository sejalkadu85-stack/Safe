package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

// ==========================================
// SAFE JOURNEY MODELS (Task 4.1 & 4.6)
// ==========================================

data class StartJourneyRequest(
    @SerializedName("start_location") val startLocation: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("start_latitude") val startLatitude: Double,
    @SerializedName("start_longitude") val startLongitude: Double,
    @SerializedName("dest_latitude") val destLatitude: Double,
    @SerializedName("dest_longitude") val destLongitude: Double,
    @SerializedName("route_polyline") val routePolyline: String = "",
    @SerializedName("estimated_arrival") val estimatedArrival: String,
    @SerializedName("share_with_all_trusted") val shareWithAllTrusted: Boolean = true,
    @SerializedName("shared_with_ids") val sharedWithIds: List<Int> = emptyList()
)

data class JourneyResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("start_location") val startLocation: String,
    @SerializedName("destination") val destination: String,
    @SerializedName("start_latitude") val startLatitude: Double,
    @SerializedName("start_longitude") val startLongitude: Double,
    @SerializedName("dest_latitude") val destLatitude: Double,
    @SerializedName("dest_longitude") val destLongitude: Double,
    @SerializedName("current_latitude") val currentLatitude: Double?,
    @SerializedName("current_longitude") val currentLongitude: Double?,
    @SerializedName("route_polyline") val routePolyline: String?,
    @SerializedName("estimated_arrival") val estimatedArrival: String,
    @SerializedName("status") val status: String,
    @SerializedName("status_display") val statusDisplay: String?,
    @SerializedName("deviation_detected") val deviationDetected: Boolean,
    @SerializedName("eta_exceeded_alerted") val etaExceededAlerted: Boolean,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_overdue") val isOverdue: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class ActiveJourneyResponse(
    @SerializedName("has_active_journey") val hasActiveJourney: Boolean,
    @SerializedName("journey") val journey: JourneyResponse?
)

data class UpdateJourneyProgressRequest(
    @SerializedName("current_latitude") val currentLatitude: Double,
    @SerializedName("current_longitude") val currentLongitude: Double
)

data class UpdateJourneyProgressResponse(
    @SerializedName("journey") val journey: JourneyResponse,
    @SerializedName("deviation_distance_meters") val deviationDistanceMeters: Double,
    @SerializedName("deviation_detected") val deviationDetected: Boolean,
    @SerializedName("new_deviation_triggered") val newDeviationTriggered: Boolean
)


// ==========================================
// DANGER ZONE MODELS (Task 4.4 & 4.9)
// ==========================================

data class DangerZoneItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius") val radius: Double,
    @SerializedName("description") val description: String,
    @SerializedName("category") val category: String,
    @SerializedName("category_display") val categoryDisplay: String?,
    @SerializedName("severity") val severity: String,
    @SerializedName("severity_display") val severityDisplay: String?,
    @SerializedName("verified") val verified: Boolean,
    @SerializedName("report_count") val reportCount: Int,
    @SerializedName("distance_meters") val distanceMeters: Double? = null,
    @SerializedName("created_at") val createdAt: String
)

data class ReportDangerZoneRequest(
    @SerializedName("title") val title: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("radius") val radius: Double = 150.0,
    @SerializedName("description") val description: String,
    @SerializedName("category") val category: String = "harassment_hotspot",
    @SerializedName("severity") val severity: String = "medium"
)


// ==========================================
// PERIODIC CHECK-IN TIMER MODELS (Task 4.2)
// ==========================================

data class StartCheckInRequest(
    @SerializedName("interval_minutes") val intervalMinutes: Int = 15
)

data class CheckInSessionResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("interval_minutes") val intervalMinutes: Int,
    @SerializedName("last_checkin") val lastCheckin: String,
    @SerializedName("next_due") val nextDue: String,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("alert_triggered") val alertTriggered: Boolean,
    @SerializedName("seconds_remaining") val secondsRemaining: Int
)

data class CheckInSessionStatusResponse(
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("session") val session: CheckInSessionResponse?
)


// ==========================================
// EVIDENCE RECORDING MODELS (Task 4.3)
// ==========================================

data class EvidenceFileItem(
    @SerializedName("id") val id: Int,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("file_type") val fileType: String,
    @SerializedName("duration_seconds") val durationSeconds: Double?,
    @SerializedName("file_size_bytes") val fileSizeBytes: Long?,
    @SerializedName("timestamp") val timestamp: String
)
