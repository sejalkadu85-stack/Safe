package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class AlertRecipientResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("recipient") val recipient: UserResponse?,
    @SerializedName("status") val status: String,
    @SerializedName("status_display") val statusDisplay: String?,
    @SerializedName("notified_via_sms") val notifiedViaSms: Boolean,
    @SerializedName("notified_via_push") val notifiedViaPush: Boolean,
    @SerializedName("notified_via_email") val notifiedViaEmail: Boolean,
    @SerializedName("notified_at") val notifiedAt: String,
    @SerializedName("acknowledged_at") val acknowledgedAt: String?,
    @SerializedName("guardian_note") val guardianNote: String?
)

data class SOSAlertResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("sender") val sender: UserResponse?,
    @SerializedName("status") val status: String,
    @SerializedName("status_display") val statusDisplay: String?,
    @SerializedName("lifecycle_status") val lifecycleStatus: String? = "open",
    @SerializedName("category") val category: String? = "unsafe_situation",
    @SerializedName("category_display") val categoryDisplay: String? = null,
    @SerializedName("escalation_tier") val escalationTier: Int = 1,
    @SerializedName("escalated_at") val escalatedAt: String? = null,
    @SerializedName("assigned_responder") val assignedResponder: Int? = null,
    @SerializedName("assigned_responder_name") val assignedResponderName: String? = null,
    @SerializedName("assigned_guardian") val assignedGuardian: Int? = null,
    @SerializedName("assigned_guardian_name") val assignedGuardianName: String? = null,
    @SerializedName("trigger_type") val triggerType: String,
    @SerializedName("trigger_type_display") val triggerTypeDisplay: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("altitude") val altitude: Double?,
    @SerializedName("accuracy_meters") val accuracyMeters: Double?,
    @SerializedName("address") val address: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("google_maps_url") val googleMapsUrl: String?,
    @SerializedName("cancellation_reason") val cancellationReason: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("resolved_at") val resolvedAt: String?,
    @SerializedName("recipients") val recipients: List<AlertRecipientResponse>?,
    @SerializedName("timeline") val timeline: List<IncidentTimelineItem>? = null
)

data class IncidentTimelineItem(
    @SerializedName("id") val id: Int,
    @SerializedName("status") val status: String,
    @SerializedName("actor_name") val actorName: String?,
    @SerializedName("note") val note: String?,
    @SerializedName("timestamp") val timestamp: String
)

data class IncidentStatusResponse(
    @SerializedName("alert") val alert: SOSAlertResponse,
    @SerializedName("timeline") val timeline: List<IncidentTimelineItem>?,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Int,
    @SerializedName("tier_timeout_seconds") val tierTimeoutSeconds: Int,
    @SerializedName("seconds_until_next_escalation") val secondsUntilNextEscalation: Int,
    @SerializedName("can_escalate") val canEscalate: Boolean
)

data class EvidenceFileResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("file") val file: String? = null,
    @SerializedName("file_url") val fileUrl: String? = null,
    @SerializedName("file_type") val fileType: String = "audio",
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class IncidentMessageItem(
    @SerializedName("id") val id: Int,
    @SerializedName("sender_name") val senderName: String,
    @SerializedName("sender_role") val senderRole: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: String
)

data class IncidentChatResponse(
    @SerializedName("alert_id") val alertId: Int,
    @SerializedName("count") val count: Int,
    @SerializedName("messages") val messages: List<IncidentMessageItem>
)

data class GuardianActiveAlertItem(
    @SerializedName("id") val id: Int,
    @SerializedName("user_name") val userName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("emergency_type") val emergencyType: String,
    @SerializedName("category") val category: String,
    @SerializedName("status") val status: String,
    @SerializedName("escalation_tier") val escalationTier: Int,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("address") val address: String,
    @SerializedName("google_maps_url") val googleMapsUrl: String?,
    @SerializedName("time_since_sos") val timeSinceSos: String,
    @SerializedName("elapsed_seconds") val elapsedSeconds: Int,
    @SerializedName("battery_level") val batteryLevel: Int?,
    @SerializedName("created_at") val createdAt: String
)

data class GuardianActiveAlertsResponse(
    @SerializedName("count") val count: Int,
    @SerializedName("active_alerts") val activeAlerts: List<GuardianActiveAlertItem>
)

data class EscalationConfigResponse(
    @SerializedName("primary_timeout_seconds") val primaryTimeoutSeconds: Int,
    @SerializedName("secondary_timeout_seconds") val secondaryTimeoutSeconds: Int,
    @SerializedName("auto_volunteer_dispatch") val autoVolunteerDispatch: Boolean,
    @SerializedName("escalation_enabled") val escalationEnabled: Boolean
)

data class TriggerSOSRequest(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerializedName("address") val address: String = "",
    @SerializedName("category") val category: String = "unsafe_situation",
    @SerializedName("message") val message: String = "EMERGENCY! I need immediate help. Here is my current location.",
    @SerializedName("trigger_type") val triggerType: String = "manual",
    @SerializedName("battery_level") val batteryLevel: Int? = null
)

data class TriggerSOSResponse(
    @SerializedName("message") val message: String,
    @SerializedName("alert") val alert: SOSAlertResponse,
    @SerializedName("recipients_notified_count") val recipientsNotifiedCount: Int
)

data class CancelSOSRequest(
    @SerializedName("reason") val reason: String = "False alarm / I am safe now."
)

data class AcknowledgeSOSRequest(
    @SerializedName("note") val note: String = "I have seen your alert and help is on the way!"
)

