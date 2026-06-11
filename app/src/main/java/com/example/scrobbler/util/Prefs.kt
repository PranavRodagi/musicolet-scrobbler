package com.example.scrobbler.util

import android.content.Context
import android.content.SharedPreferences
import com.example.scrobbler.model.PendingScrobble
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("scrobbler_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API_KEY, v).apply()

    var sharedSecret: String
        get() = prefs.getString(KEY_SECRET, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SECRET, v).apply()

    var sessionKey: String
        get() = prefs.getString(KEY_SESSION, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SESSION, v).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    val isAuthenticated: Boolean
        get() = sessionKey.isNotBlank()

    var debugLogging: Boolean
        get() = prefs.getBoolean(KEY_DEBUG, false)
        set(v) = prefs.edit().putBoolean(KEY_DEBUG, v).apply()

    fun getPendingQueue(): MutableList<PendingScrobble> {
        val json = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        val type = object : TypeToken<MutableList<PendingScrobble>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun savePendingQueue(queue: List<PendingScrobble>) {
        prefs.edit().putString(KEY_QUEUE, gson.toJson(queue)).apply()
    }

    fun addToPendingQueue(scrobble: PendingScrobble) {
        val q = getPendingQueue()
        q.add(scrobble)
        savePendingQueue(q)
    }

    fun clearPendingQueue() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_API_KEY  = "api_key"
        private const val KEY_SECRET   = "shared_secret"
        private const val KEY_SESSION  = "session_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_QUEUE    = "pending_queue"
        private const val KEY_DEBUG    = "debug_logging"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}