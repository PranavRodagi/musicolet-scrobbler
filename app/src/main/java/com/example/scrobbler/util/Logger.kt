package com.example.scrobbler.util

import android.util.Log

object Logger {
    private const val TAG = "MusicoletScrobbler"
    private var debugEnabled = false

    fun init(debug: Boolean) { debugEnabled = debug }

    fun d(msg: String) { if (debugEnabled) Log.d(TAG, msg) }
    fun i(msg: String) { Log.i(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
}