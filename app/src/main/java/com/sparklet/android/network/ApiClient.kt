package com.sparklet.android.network

import com.sparklet.android.config.AppConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Thin OkHttp wrapper. The bearer token is passed in per-call rather than
// read from an app-wide session directly, so this stays a plain, testable
// object with no dependency on where the token is stored.
object ApiClient {
    private val client = OkHttpClient()

    // get/post/decode are inline (needed for their reified type params), so
    // their bodies get copied into call sites in other files — a plain
    // `private val` here would be invisible from there and fail to compile.
    // @PublishedApi internal is Kotlin's documented escape hatch: real
    // visibility stays module-internal, but inline call sites can still see it.
    @PublishedApi
    internal val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> get(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        token: String?,
    ): T {
        val urlBuilder = AppConfig.apiBaseUrl.newBuilder().addPathSegments(path)
        for ((key, value) in query) urlBuilder.addQueryParameter(key, value)
        val builder = Request.Builder().url(urlBuilder.build()).get()
        token?.let { builder.header("Authorization", "Bearer $it") }
        return decode(execute(builder.build()))
    }

    suspend inline fun <reified B, reified T> post(
        path: String,
        body: B,
        token: String?,
    ): T {
        val requestBody = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(AppConfig.apiBaseUrl.newBuilder().addPathSegments(path).build())
            .post(requestBody)
        token?.let { builder.header("Authorization", "Bearer $it") }
        return decode(execute(builder.build()))
    }

    // No caller needs the `{ ok: true }` body back — avoids the awkward
    // "decode into a type I'm discarding" shape a generic delete<T> forces.
    suspend fun delete(path: String, token: String?) {
        val builder = Request.Builder()
            .url(AppConfig.apiBaseUrl.newBuilder().addPathSegments(path).build())
            .delete()
        token?.let { builder.header("Authorization", "Bearer $it") }
        execute(builder.build())
    }

    // Kept non-generic and non-inline (unlike get/post) so the actual
    // network call and status handling exist exactly once.
    //
    // The whole thing — not just newCall().execute() — has to run inside
    // withContext(Dispatchers.IO): response.body.string() below does its own
    // blocking socket read for a chunked response (no Content-Length to
    // pre-buffer against), so calling it from whatever dispatcher invoked
    // execute() risks NetworkOnMainThreadException. This only showed up once
    // a response was large enough to need a read past OkHttp's initial
    // buffered chunk — small responses (e.g. /api/profile) happened to be
    // fully readable from that buffer and never touched the socket here.
    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException.Transport(e)
        }
        response.use {
            val bodyString = it.body?.string().orEmpty()
            if (it.code == 401) throw ApiException.Unauthorized
            if (!it.isSuccessful) throw ApiException.Server(it.code, bodyString)
            return@withContext bodyString
        }
    }

    @PublishedApi
    internal inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString(body)
        } catch (e: SerializationException) {
            throw ApiException.Decoding(e)
        }
}
