package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/notifications (sparklet/src/app/api/notifications/route.ts).
// adminAlerts/friendAlerts aren't persisted rows — they're live-computed
// action items (open reports, cards awaiting review, pending friend requests;
// see sparklet/src/lib/notifications.ts) that clear themselves once resolved,
// not something a client ever marks read.
@Serializable
data class NotificationsResponse(
    val unreadCount: Int,
    val adminAlerts: List<NotificationAlert>,
    val friendAlerts: List<NotificationAlert>,
    val notifications: List<NotificationItem>,
)

@Serializable
data class NotificationAlert(
    val id: String,
    val label: String,
    val count: Int,
)

@Serializable
data class NotificationItem(
    val id: String,
    val actorName: String,
    val cardId: String,
    val cardTitle: String,
    val preview: String? = null,
    val createdAt: String,
    val read: Boolean,
)
