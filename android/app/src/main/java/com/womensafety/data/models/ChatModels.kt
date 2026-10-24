package com.womensafety.data.models

import com.google.gson.annotations.SerializedName

data class MessageResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("conversation") val conversation: Int,
    @SerializedName("sender") val sender: UserResponse?,
    @SerializedName("content") val content: String,
    @SerializedName("message_type") val messageType: String = "text",
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("is_me") val isMe: Boolean = false
)

data class ConversationResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("participants") val participants: List<UserResponse> = emptyList(),
    @SerializedName("last_message") val lastMessage: MessageResponse?,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class ConversationDetailResponse(
    @SerializedName("conversation_id") val conversationId: Int,
    @SerializedName("messages") val messages: List<MessageResponse> = emptyList()
)

data class SendMessageRequest(
    @SerializedName("conversation_id") val conversationId: Int? = null,
    @SerializedName("recipient_id") val recipientId: Int? = null,
    @SerializedName("content") val content: String,
    @SerializedName("message_type") val messageType: String = "text"
)

data class QuickTemplateResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("is_default") val isDefault: Boolean = false,
    @SerializedName("is_custom") val isCustom: Boolean = false
)

data class CreateQuickTemplateRequest(
    @SerializedName("content") val content: String,
    @SerializedName("title") val title: String? = null
)

data class NotificationItemResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("notification_type") val notificationType: String,
    @SerializedName("notification_type_display") val notificationTypeDisplay: String?,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("data") val data: Map<String, Any?>? = null,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("created_at") val createdAt: String
)

data class NotificationHistoryResponse(
    @SerializedName("total_unread") val totalUnread: Int = 0,
    @SerializedName("notifications") val notifications: List<NotificationItemResponse> = emptyList()
)
