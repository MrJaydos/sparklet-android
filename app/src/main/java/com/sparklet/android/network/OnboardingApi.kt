package com.sparklet.android.network

import com.sparklet.android.model.Category
import kotlinx.serialization.Serializable

// Mirrors GET /api/categories and GET/POST /api/interests
// (sparklet/src/app/api/categories/route.ts, .../interests/route.ts).
class OnboardingApi(private val client: ApiClient = ApiClient) {

    @Serializable
    private data class CategoriesResponse(val categories: List<Category>)

    @Serializable
    private data class InterestsRequest(val categorySlugs: List<String>)

    @Serializable
    private data class InterestsResponse(val ok: Boolean)

    @Serializable
    private data class FetchInterestsResponse(val categorySlugs: List<String>)

    // No auth required — the same query the web's onboarding and signed-out
    // feed pages already run server-side.
    suspend fun fetchCategories(token: String?): List<Category> =
        client.get<CategoriesResponse>("api/categories", token = token).categories

    // The `UserInterest` table — not either platform's local storage — is the
    // durable, cross-device source of truth for the topic filter, so this is
    // read on feed load rather than cached locally.
    suspend fun fetchInterests(token: String?): List<String> =
        client.get<FetchInterestsResponse>("api/interests", token = token).categorySlugs

    // Submitting an empty selection (the "show me everything" skip) still
    // completes onboarding server-side — see the route's own comment.
    suspend fun submitInterests(categorySlugs: List<String>, token: String?) {
        client.post<InterestsRequest, InterestsResponse>(
            "api/interests",
            InterestsRequest(categorySlugs),
            token,
        )
    }
}
