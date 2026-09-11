package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/profile/details (sparklet/src/app/api/profile/details/
// route.ts) — everything the web profile page shows beyond the lightweight
// xp/streak numbers already in ProfileResponse: badges, history, notebook
// (saved cards), top categories, and due-reviews count. Friends/friendCode
// are deliberately not duplicated here — they come from /api/friends.
@Serializable
data class ProfileDetailsResponse(
    val id: String,
    val name: String,
    val email: String,
    val premium: Boolean,
    val xp: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val freezesAvailable: Int,
    val totalViewed: Int,
    val dueReviews: Int,
    val badges: List<Badge>,
    val topCategories: List<TopCategory>,
    val savedCards: List<SavedCard>,
    val history: List<HistoryEntry>,
)

@Serializable
data class Badge(
    val key: String,
    val icon: String,
    val name: String,
    val value: Int,
    val earnedTier: BadgeTier? = null,
    val nextTier: BadgeTier? = null,
)

@Serializable
data class BadgeTier(
    val threshold: Int,
    val label: String,
)

@Serializable
data class TopCategory(
    val name: String,
    val icon: String,
    val colorHex: String,
    val count: Int,
)

@Serializable
data class SavedCard(
    val cardId: String,
    val title: String,
    val icon: String,
    val colorHex: String,
)

// History can legitimately repeat a card (re-read after a spaced-repetition
// review), so cardId alone is not a stable unique key — list callers must key
// by index rather than by cardId.
@Serializable
data class HistoryEntry(
    val cardId: String,
    val title: String,
    val icon: String,
    val colorHex: String,
    val `when`: String,
)
