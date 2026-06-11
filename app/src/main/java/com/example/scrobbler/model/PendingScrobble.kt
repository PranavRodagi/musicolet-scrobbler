package com.example.scrobbler.model

data class PendingScrobble(
    val title: String,
    val artist: String,
    val album: String,
    val timestampSec: Long,
    val listenedMs: Long,
    val retryCount: Int = 0
) {
    companion object {
        fun from(session: PlaybackSession) = PendingScrobble(
            title        = session.track.title,
            artist       = session.track.artist,
            album        = session.track.album,
            timestampSec = session.startTimestampSec,
            listenedMs   = session.totalListenedMs()
        )
    }
}