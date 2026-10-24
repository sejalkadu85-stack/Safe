package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class TrustedContactResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("owner") val owner: UserResponse?,
    @SerializedName("contact_user") val contactUser: UserResponse?,
    @SerializedName("name") val name: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("tier") val tier: String?,
    @SerializedName("tier_display") val tierDisplay: String?,
    @SerializedName("relationship") val relationship: String,
    @SerializedName("relationship_display") val relationshipDisplay: String?,
    @SerializedName("status") val status: String,
    @SerializedName("status_display") val statusDisplay: String?,
    @SerializedName("is_verified") val isVerified: Boolean = false,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("notify_via_sms") val notifyViaSms: Boolean,
    @SerializedName("notify_via_email") val notifyViaEmail: Boolean,
    @SerializedName("notify_via_push") val notifyViaPush: Boolean,
    @SerializedName("created_at") val createdAt: String
)

data class AddContactRequest(
    @SerializedName("name") val name: String = "",
    @SerializedName("phone_number") val phoneNumber: String = "",
    @SerializedName("email_or_phone") val emailOrPhone: String = "",
    @SerializedName("tier") val tier: String = "primary",
    @SerializedName("relationship") val relationship: String = "other",
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("notify_via_sms") val notifyViaSms: Boolean = true,
    @SerializedName("notify_via_email") val notifyViaEmail: Boolean = true,
    @SerializedName("notify_via_push") val notifyViaPush: Boolean = true
)

data class RespondContactRequest(
    @SerializedName("action") val action: String // "accept", "reject", "block"
)

data class AddContactResponse(
    @SerializedName("message") val message: String,
    @SerializedName("contact") val contact: TrustedContactResponse
)

