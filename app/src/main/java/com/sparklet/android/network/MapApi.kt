package com.sparklet.android.network

import com.sparklet.android.model.MapResponse

// Mirrors GET /api/map (sparklet/src/app/api/map/route.ts).
class MapApi(private val client: ApiClient = ApiClient) {
    suspend fun fetch(token: String?): MapResponse =
        client.get("api/map", token = token)
}
