package com.example.scrobbler.service

import com.example.scrobbler.model.PendingScrobble
import com.example.scrobbler.model.PlaybackSession
import com.example.scrobbler.model.Track
import com.example.scrobbler.util.Logger

class PlaybackStateManager {

    private var currentSession: PlaybackSession? = null
    private val DEBOUNCE_MS = 1500L
    private var lastEventMs = 0L

    @Synchronized
    fun onNotification(track: Track, paused: Boolean): SessionEvent {
        val now = System.currentTimeMillis()

        if (now - lastEventMs < DEBOUNCE_MS) {
            val current = currentSession
            if (current != null && current.track.id == track.id) {
                Logger.d("Debounced duplicate for '${track.title}'")
                return SessionEvent.Ignored
            }
        }
        lastEventMs = now

        val session = currentSession

        return when {
            session == null -> {
                startNewSession(track, now, paused)
                SessionEvent.NewTrack(currentSession!!)
            }
            session.track.id == track.id && paused && session.isPlaying -> {
                pauseSession(now)
                SessionEvent.Paused
            }
            session.track.id == track.id && !paused && !session.isPlaying -> {
                resumeSession(now)
                SessionEvent.Resumed
            }
            session.track.id == track.id -> {
                Logger.d("Same track same state, ignoring")
                SessionEvent.Ignored
            }
            else -> {
                val scrobbleCandidate = finalizeSession(now)
                startNewSession(track, now, paused)
                SessionEvent.TrackChanged(scrobbleCandidate, currentSession!!)
            }
        }
    }

    @Synchronized
    fun onPlaybackStopped(): PendingScrobble? {
        val now = System.currentTimeMillis()
        return finalizeSession(now)
    }

    @Synchronized
    fun getCurrentSession(): PlaybackSession? = currentSession

    @Synchronized
    fun isPlaying(): Boolean = currentSession?.isPlaying == true

    private fun startNewSession(track: Track, now: Long, paused: Boolean) {
        currentSession = PlaybackSession(
            track        = track,
            startTimeMs  = now,
            lastResumeMs = now,
            isPlaying    = !paused
        )
        Logger.i("New session: '${track.artist} - ${track.title}' paused=$paused")
    }

    private fun pauseSession(now: Long) {
        val session = currentSession ?: return
        session.listenedMs += (now - session.lastResumeMs)
        session.isPlaying   = false
        Logger.d("Paused '${session.track.title}' — listened: ${session.listenedMs}ms")
    }

    private fun resumeSession(now: Long) {
        val session = currentSession ?: return
        session.lastResumeMs = now
        session.isPlaying    = true
        Logger.d("Resumed '${session.track.title}'")
    }

    private fun finalizeSession(now: Long): PendingScrobble? {
        val session = currentSession ?: return null

        if (session.isPlaying) {
            session.listenedMs += (now - session.lastResumeMs)
            session.isPlaying   = false
        }

        val candidate = if (!session.scrobbled && session.track.isValid && session.shouldScrobble()) {
            session.scrobbled = true
            Logger.i("Queueing scrobble: '${session.track.artist} - ${session.track.title}' " +
                    "listened=${session.listenedMs}ms")
            PendingScrobble.from(session)
        } else {
            Logger.d("Not scrobbling '${session.track.title}': listened=${session.listenedMs}ms")
            null
        }

        currentSession = null
        return candidate
    }
}

sealed class SessionEvent {
    data class NewTrack(val session: PlaybackSession) : SessionEvent()
    data class TrackChanged(
        val scrobbleCandidate: PendingScrobble?,
        val newSession: PlaybackSession
    ) : SessionEvent()
    object Paused  : SessionEvent()
    object Resumed : SessionEvent()
    object Ignored : SessionEvent()
}