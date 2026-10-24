package com.womensafety.data.api

import com.womensafety.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/register/")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthResponse>

    @Multipart
    @POST("auth/register/")
    suspend fun registerMultipart(
        @PartMap parts: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>,
        @Part idProofDocument: MultipartBody.Part?
    ): Response<AuthResponse>

    @POST("auth/login/")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("auth/logout/")
    suspend fun logout(
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    // Profile
    @GET("auth/profile/")
    suspend fun getProfile(): Response<UserResponse>

    @PUT("auth/profile/")
    suspend fun updateProfile(
        @Body profileData: Map<String, Any?>
    ): Response<Map<String, Any?>>

    @Multipart
    @POST("auth/profile/photo/")
    suspend fun uploadProfilePhoto(
        @Part photo: MultipartBody.Part
    ): Response<Map<String, String>>

    @GET("auth/profile/{id}/")
    suspend fun getUserDetailById(
        @Path("id") id: Int
    ): Response<UserResponse>

    // OTP
    @POST("auth/otp/send/")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): Response<GenericResponse>

    @POST("auth/otp/verify/")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<GenericResponse>

    // Trusted Contacts
    @POST("alerts/contacts/add/")
    suspend fun addContact(
        @Body request: AddContactRequest
    ): Response<AddContactResponse>

    @GET("alerts/contacts/")
    suspend fun getContacts(
        @Query("status") status: String? = null
    ): Response<List<TrustedContactResponse>>

    @GET("alerts/contacts/pending/")
    suspend fun getPendingRequests(): Response<List<TrustedContactResponse>>

    @PUT("alerts/contacts/{id}/respond/")
    suspend fun respondToContactRequest(
        @Path("id") id: Int,
        @Body request: RespondContactRequest
    ): Response<AddContactResponse>

    @PUT("alerts/contacts/{id}/")
    suspend fun updateContact(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<TrustedContactResponse>

    @POST("alerts/contacts/{id}/verify/")
    suspend fun verifyContact(
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    @DELETE("alerts/contacts/{id}/")
    suspend fun deleteContact(
        @Path("id") id: Int
    ): Response<GenericResponse>

    // Emergency SOS & Incident Lifecycle
    @POST("alerts/sos/trigger/")
    suspend fun triggerSOS(
        @Body request: TriggerSOSRequest
    ): Response<TriggerSOSResponse>

    @GET("alerts/sos/active/")
    suspend fun getActiveSOSAlerts(): Response<List<SOSAlertResponse>>

    @GET("alerts/sos/history/")
    suspend fun getSOSHistory(): Response<List<SOSAlertResponse>>

    @GET("alerts/sos/{id}/")
    suspend fun getSOSDetail(
        @Path("id") id: Int
    ): Response<SOSAlertResponse>

    @GET("alerts/sos/{id}/status/")
    suspend fun getSOSIncidentStatus(
        @Path("id") id: Int
    ): Response<IncidentStatusResponse>

    @POST("alerts/sos/{id}/escalate/")
    suspend fun escalateSOSIncident(
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    @POST("alerts/sos/{id}/respond/")
    suspend fun respondToSOSIncident(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("alerts/sos/{id}/resolve/")
    suspend fun resolveSOSIncident(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("alerts/sos/{id}/close/")
    suspend fun closeSOSIncident(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @PUT("alerts/sos/{id}/cancel/")
    suspend fun cancelSOS(
        @Path("id") id: Int,
        @Body request: CancelSOSRequest
    ): Response<Map<String, Any>>

    @PUT("alerts/sos/{id}/acknowledge/")
    suspend fun acknowledgeSOS(
        @Path("id") id: Int,
        @Body request: AcknowledgeSOSRequest
    ): Response<Map<String, Any>>

    // Emergency Incident Chat
    @GET("alerts/sos/{id}/chat/")
    suspend fun getIncidentChat(
        @Path("id") id: Int
    ): Response<IncidentChatResponse>

    @POST("alerts/sos/{id}/chat/")
    suspend fun sendIncidentChatMessage(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    // Guardian Dashboard Active Alerts
    @GET("alerts/guardian/active/")
    suspend fun getGuardianActiveAlerts(): Response<GuardianActiveAlertsResponse>

    // Admin Escalation Settings
    @GET("alerts/admin/escalation-config/")
    suspend fun getEscalationConfig(): Response<EscalationConfigResponse>

    @PUT("alerts/admin/escalation-config/")
    suspend fun updateEscalationConfig(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Map<String, Any>>


    // Live Location & GPS Tracking
    @POST("location/update/")
    suspend fun updateLocation(
        @Body request: LocationUpdateRequest
    ): Response<Map<String, Any>>

    @GET("location/live/{userId}/")
    suspend fun getLiveLocation(
        @Path("userId") userId: Int
    ): Response<LiveLocationResponse>

    @GET("location/history/{userId}/")
    suspend fun getLocationHistory(
        @Path("userId") userId: Int,
        @Query("hours") hours: Int? = 6,
        @Query("limit") limit: Int? = 100
    ): Response<List<LocationLogResponse>>

    @POST("location/share/")
    suspend fun startLocationShare(
        @Body request: StartLocationShareRequest
    ): Response<Map<String, Any>>

    @POST("location/share/stop/")
    suspend fun stopLocationShare(): Response<GenericResponse>

    @GET("location/share/status/")
    suspend fun getLocationShareStatus(): Response<LocationShareStatusResponse>

    // Safe Zones & Geofencing
    @GET("location/safezone/")
    suspend fun getSafeZones(): Response<List<SafeZoneResponse>>

    @POST("location/safezone/")
    suspend fun createSafeZone(
        @Body request: CreateSafeZoneRequest
    ): Response<SafeZoneResponse>

    @GET("location/safezone/{id}/")
    suspend fun getSafeZoneDetail(
        @Path("id") id: Int
    ): Response<SafeZoneResponse>

    @PUT("location/safezone/{id}/")
    suspend fun updateSafeZone(
        @Path("id") id: Int,
        @Body request: CreateSafeZoneRequest
    ): Response<SafeZoneResponse>

    @DELETE("location/safezone/{id}/")
    suspend fun deleteSafeZone(
        @Path("id") id: Int
    ): Response<GenericResponse>

    @POST("location/safezone/check/")
    suspend fun checkGeofence(
        @Body request: CheckGeofenceRequest
    ): Response<CheckGeofenceResponse>

    @GET("location/safezone/events/")
    suspend fun getSafeZoneEvents(): Response<List<SafeZoneEventResponse>>

    // In-App Messaging
    @GET("messages/conversations/")
    suspend fun getConversations(): Response<List<ConversationResponse>>

    @GET("messages/{conversation_id}/")
    suspend fun getConversationMessages(
        @Path("conversation_id") conversationId: Int,
        @Query("limit") limit: Int? = 50
    ): Response<ConversationDetailResponse>

    @POST("messages/send/")
    suspend fun sendMessage(
        @Body request: SendMessageRequest
    ): Response<MessageResponse>

    @PUT("messages/{id}/read/")
    suspend fun markMessageRead(
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    @GET("messages/quick-templates/")
    suspend fun getQuickTemplates(): Response<List<QuickTemplateResponse>>

    @POST("messages/quick-templates/")
    suspend fun createQuickTemplate(
        @Body request: CreateQuickTemplateRequest
    ): Response<QuickTemplateResponse>

    // Notifications
    @GET("notifications/")
    suspend fun getNotifications(
        @Query("unread") unread: Boolean? = null
    ): Response<NotificationHistoryResponse>

    @POST("notifications/token/")
    suspend fun registerDeviceToken(
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @PUT("notifications/{id}/read/")
    suspend fun markNotificationRead(
        @Path("id") id: Int
    ): Response<Map<String, Any>>

    // ==========================================
    // PHASE 4: SAFE JOURNEY (Task 4.1 & 4.6)
    // ==========================================
    @POST("journey/start/")
    suspend fun startJourney(
        @Body request: StartJourneyRequest
    ): Response<JourneyResponse>

    @PUT("journey/{id}/update/")
    suspend fun updateJourneyProgress(
        @Path("id") id: Int,
        @Body request: UpdateJourneyProgressRequest
    ): Response<UpdateJourneyProgressResponse>

    @PUT("journey/{id}/complete/")
    suspend fun completeJourney(
        @Path("id") id: Int
    ): Response<JourneyResponse>

    @GET("journey/active/")
    suspend fun getActiveJourney(
        @Query("user_id") userId: Int? = null
    ): Response<ActiveJourneyResponse>

    // ==========================================
    // PHASE 4: PERIODIC CHECK-IN TIMER (Task 4.2)
    // ==========================================
    @POST("checkin/start/")
    suspend fun startCheckInSession(
        @Body request: StartCheckInRequest
    ): Response<CheckInSessionResponse>

    @POST("checkin/respond/")
    suspend fun respondCheckInSession(): Response<Map<String, Any>>

    @DELETE("checkin/stop/")
    suspend fun stopCheckInSession(): Response<Map<String, Any>>

    @GET("checkin/status/")
    suspend fun getCheckInStatus(): Response<CheckInSessionStatusResponse>

    // ==========================================
    // PHASE 4: DANGER ZONES (Task 4.4 & 4.9)
    // ==========================================
    @POST("dangerzones/report/")
    suspend fun reportDangerZone(
        @Body request: ReportDangerZoneRequest
    ): Response<DangerZoneItem>

    @GET("dangerzones/")
    suspend fun getDangerZones(
        @Query("category") category: String? = null
    ): Response<List<DangerZoneItem>>

    @GET("dangerzones/nearby/")
    suspend fun getNearbyDangerZones(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 1500.0
    ): Response<List<DangerZoneItem>>

    // ==========================================
    // PHASE 5: VOLUNTEER NETWORK & HELPLINES (Task 5.1 & 5.2)
    // ==========================================
    @GET("helplines/")
    suspend fun getHelplines(
        @Query("category") category: String? = null,
        @Query("search") search: String? = null
    ): Response<HelplineResponse>

    @GET("volunteers/nearby/")
    suspend fun getNearbyVolunteers(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius") radius: Double = 10000.0
    ): Response<VolunteerListResponse>

    @POST("volunteers/register/")
    suspend fun registerVolunteer(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<VolunteerProfileResponse>

    @GET("volunteers/profile/")
    suspend fun getVolunteerProfile(): Response<VolunteerProfileResponse>

    @PUT("volunteers/profile/")
    suspend fun updateVolunteerProfile(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<VolunteerProfileResponse>

    @POST("volunteers/request/")
    suspend fun createVolunteerRequest(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<VolunteerRequestCreateResponse>

    @GET("volunteers/requests/")
    suspend fun getVolunteerRequests(
        @Query("role") role: String? = null
    ): Response<VolunteerRequestsResponse>

    @PUT("volunteers/requests/{id}/respond/")
    suspend fun respondVolunteerRequest(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @POST("volunteers/requests/{id}/rate/")
    suspend fun rateVolunteerRequest(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // ==========================================
    // TASK 5.4: BATTERY-LOW AUTO ALERT
    // ==========================================
    @POST("alerts/battery-low/")
    suspend fun sendBatteryLowAlert(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, Any>>

    // ==========================================
    // TASK 5.3: ADMIN DASHBOARD APIs
    // ==========================================
    @GET("admin/users/")
    suspend fun getAdminUsers(
        @Query("role") role: String? = null
    ): Response<Map<String, Any>>

    @PUT("admin/users/{id}/ban/")
    suspend fun banUser(
        @Path("id") id: Int,
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @GET("admin/sos-logs/")
    suspend fun getAdminSosLogs(): Response<Map<String, Any>>

    @GET("admin/stats/")
    suspend fun getAdminStats(): Response<Map<String, Any>>

    @GET("admin/dangerzones/pending/")
    suspend fun getPendingDangerZones(): Response<Map<String, Any>>

    @POST("admin/broadcast/")
    suspend fun sendBroadcast(
        @Body body: Map<String, String>
    ): Response<Map<String, Any>>

    @GET("admin/volunteers/pending/")
    suspend fun getPendingVolunteers(): Response<Map<String, Any>>

    @PUT("admin-panel/api/volunteers/{id}/verify/")
    suspend fun verifyVolunteer(
        @Path("id") id: Int,
        @Body body: Map<String, Boolean>
    ): Response<Map<String, Any>>

    // Voice Notes & Evidence Upload
    @Multipart
    @POST("evidence/upload/")
    suspend fun uploadEvidence(
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: okhttp3.RequestBody,
        @Part("duration_seconds") durationSeconds: okhttp3.RequestBody? = null,
        @Part("sos_alert_id") sosAlertId: okhttp3.RequestBody? = null
    ): Response<EvidenceFileResponse>
}

