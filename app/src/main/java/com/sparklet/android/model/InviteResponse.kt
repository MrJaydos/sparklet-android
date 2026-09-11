package com.sparklet.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors POST /api/invite/[refId]/accept (sparklet/src/app/api/invite/
// [refId]/accept/route.ts), which ports the auto-friend + streak-freeze
// reward logic out of the web's src/app/invite/[refId]/page.tsx.
@Serializable
data class InviteResponse(
    val status: InviteStatus,
    val referrerName: String? = null,
    val rewardGranted: Boolean,
)

@Serializable
enum class InviteStatus {
    @SerialName("invalid")
    INVALID,

    // Following your own invite link. Named SELF because `self` is not a
    // Kotlin keyword but reads badly as an enum constant; the wire value is
    // what matters and is pinned by @SerialName.
    @SerialName("self")
    SELF,

    @SerialName("friended")
    FRIENDED,

    @SerialName("already")
    ALREADY,
}
