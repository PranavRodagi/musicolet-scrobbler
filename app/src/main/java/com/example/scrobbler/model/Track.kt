package com.example.scrobbler.model

data class Track(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L
) {
    val id: String get() = "${artist.trim().lowercase()}|${title.trim().lowercase()}"
    val isValid: Boolean get() = title.isNotBlank() && artist.isNotBlank()
}

data class PlaybackSession(
    val track: Track,
    val startTimeMs: Long,
    var listenedMs: Long = 0L,
    var lastResumeMs: Long = startTimeMs,
    var isPlaying: Boolean = true,
    var nowPlayingSent: Boolean = false,
    var scrobbled: Boolean = false
) {
    fun totalListenedMs(): Long {
        return if (isPlaying) {
            listenedMs + (System.currentTimeMillis() - lastResumeMs)
        } else {
            listenedMs
        }
    }

    fun shouldScrobble(): Boolean {
        val listened = totalListenedMs()
        val halfDuration = if (track.durationMs > 0) track.durationMs / 2 else Long.MAX_VALUE
        return listened >= 240_000L || listened >= halfDuration
    }

    val startTimestampSec: Long get() = startTimeMs / 1000L
}