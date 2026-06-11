package com.example.scrobbler.api

import android.util.Log
import com.example.scrobbler.model.PendingScrobble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LastFmApiClient(
    private val apiKey: String,
    private val sharedSecret: String
) {
    companion object {
        private const val TAG = "LastFmClient"
        private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        private const val MAX_RETRIES = 3
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun authenticate(username: String, password: String): String? =
        withContext(Dispatchers.IO) {
            val params = sortedMapOf(
                "api_key"  to apiKey,
                "method"   to "auth.getMobileSession",
                "password" to password,
                "username" to username
            )
            params["api_sig"] = sign(params)
            params["format"]  = "json"
            try {
                val response = postForm(params)
                val json = JSONObject(response)
                if (json.has("session")) {
                    json.getJSONObject("session").getString("key")
                } else {
                    Log.e(TAG, "Auth failed: $response")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth exception", e)
                null
            }
        }

    suspend fun updateNowPlaying(
        sessionKey: String,
        track: String,
        artist: String,
        album: String = "",
        durationSec: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        val params = sortedMapOf(
            "api_key" to apiKey,
            "artist"  to artist,
            "method"  to "track.updateNowPlaying",
            "sk"      to sessionKey,
            "track"   to track
        )
        if (album.isNotBlank()) params["album"] = album
        if (durationSec > 0)   params["duration"] = durationSec.toString()
        params["api_sig"] = sign(params)
        params["format"]  = "json"
        try {
            val response = postFormWithRetry(params)
            val json = JSONObject(response)
            val ok = !json.has("error")
            if (!ok) Log.w(TAG, "NowPlaying rejected: $response")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "NowPlaying exception", e)
            false
        }
    }

    suspend fun scrobble(
        sessionKey: String,
        scrobble: PendingScrobble
    ): Boolean = withContext(Dispatchers.IO) {
        val params = sortedMapOf(
            "api_key"      to apiKey,
            "artist[0]"    to scrobble.artist,
            "method"       to "track.scrobble",
            "sk"           to sessionKey,
            "timestamp[0]" to scrobble.timestampSec.toString(),
            "track[0]"     to scrobble.title
        )
        if (scrobble.album.isNotBlank()) params["album[0]"] = scrobble.album
        params["api_sig"] = sign(params)
        params["format"]  = "json"
        try {
            val response = postFormWithRetry(params)
            val json = JSONObject(response)
            if (json.has("error")) {
                Log.w(TAG, "Scrobble rejected: $response")
                false
            } else {
                val accepted = json.optJSONObject("scrobbles")
                    ?.optJSONObject("@attr")
                    ?.optInt("accepted", 0) ?: 0
                Log.d(TAG, "Scrobble accepted=$accepted for '${scrobble.title}'")
                accepted > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scrobble exception", e)
            false
        }
    }

    suspend fun scrobbleBatch(
        sessionKey: String,
        batch: List<PendingScrobble>
    ): Int = withContext(Dispatchers.IO) {
        if (batch.isEmpty()) return@withContext 0
        val limited = batch.take(50)
        val params = sortedMapOf<String, String>(
            "api_key" to apiKey,
            "method"  to "track.scrobble",
            "sk"      to sessionKey
        )
        limited.forEachIndexed { i, s ->
            params["artist[$i]"]    = s.artist
            params["track[$i]"]     = s.title
            params["timestamp[$i]"] = s.timestampSec.toString()
            if (s.album.isNotBlank()) params["album[$i]"] = s.album
        }
        params["api_sig"] = sign(params)
        params["format"]  = "json"
        try {
            val response = postFormWithRetry(params)
            val json = JSONObject(response)
            json.optJSONObject("scrobbles")
                ?.optJSONObject("@attr")
                ?.optInt("accepted", 0) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Batch scrobble exception", e)
            0
        }
    }

    fun sign(params: Map<String, String>): String {
        val sb = StringBuilder()
        params.toSortedMap()
            .filter { it.key != "format" && it.key != "callback" }
            .forEach { (k, v) -> sb.append(k).append(v) }
        sb.append(sharedSecret)
        return md5(sb.toString())
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return BigInteger(1, bytes).toString(16).padStart(32, '0')
    }

    private fun postForm(params: Map<String, String>): String {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder().url(BASE_URL).post(body).build()
        http.newCall(req).execute().use { resp ->
            return resp.body?.string() ?: throw Exception("Empty response")
        }
    }

    private fun postFormWithRetry(
        params: Map<String, String>,
        attempts: Int = MAX_RETRIES
    ): String {
        var lastException: Exception? = null
        repeat(attempts) { attempt ->
            try {
                return postForm(params)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
                Thread.sleep(1000L * (attempt + 1))
            }
        }
        throw lastException ?: Exception("All retries failed")
    }
}