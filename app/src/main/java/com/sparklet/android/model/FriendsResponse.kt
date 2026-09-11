package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/friends (sparklet/src/app/api/friends/route.ts), a route
// added specifically to give native clients a JSON source for the friends
// list — the web app builds this server-side inside the profile page's own
// query (src/app/profile/page.tsx), which a native client has no equivalent of.
@Serializable
data class FriendsResponse(
    val friendCode: String,
    val friends: List<FriendRow>,
    val incoming: List<FriendRow>,
    val outgoing: List<FriendRow>,
)

@Serializable
data class FriendRow(
    val friendshipId: String,
    val name: String,
    val email: String,
)
