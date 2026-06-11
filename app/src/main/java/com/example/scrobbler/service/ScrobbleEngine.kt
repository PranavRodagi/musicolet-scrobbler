package com.example.scrobbler.service

import android.content.Context
import com.example.scrobbler.api.LastFmApiClient
import com.example.scrobbler.model.PendingScrobble
import com.example.scrobbler.model.PlaybackSession
import com.example.scrobbler.util.Logger
import com.example.scrobbler.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScrobbleEngine(private val context: Context) {

    private val prefs  = Prefs.get(context)
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client: LastFmApiClient
        get() = LastFmApiClient(prefs.apiKey, prefs.sharedSecret)

    fun sendNowPlaying(session: PlaybackSession) {
        if (!prefs.isAuthenticated) return
        if (session.nowPlayingSent) return
        session.nowPlayingSent = true

        val track = session.track
        scope.launch {
            val ok = client.updateNowPlaying(
                sessionKey  = prefs.sessionKey,
                track       = track.title,
                artist      = track.artist,
                album       = track.album,
                durationSec = (track.durationMs / 1000).toInt()
            )
            Logger.d("NowPlaying '${track.title}': $ok")
        }
    }

    fun scrobble(pending: PendingScrobble) {
        if (!prefs.isAuthenticated) {
            Logger.w("Not authenticated; queuing scrobble offline")
            prefs.addToPendingQueue(pending)
            return
        }

        scope.launch {
            val ok = client.scrobble(prefs.sessionKey, pending)
            if (ok) {
                Logger.i("Scrobbled: '${pending.artist} - ${pending.title}'")
                flushOfflineQueue()
            } else {
                Logger.w("Scrobble failed; queuing for retry")
                prefs.addToPendingQueue(pending)
            }
        }
    }

    fun flushOfflineQueue() {
        if (!prefs.isAuthenticated) return
        val queue = prefs.getPendingQueue()
        if (queue.isEmpty()) return

        Logger.i("Flushing ${queue.size} pending scrobbles")
        scope.launch {
            val accepted = client.scrobbleBatch(prefs.sessionKey, queue)
            if (accepted == queue.size) {
                Logger.i("All ${accepted} queued scrobbles accepted")
                prefs.clearPendingQueue()
            } else if (accepted > 0) {
                val remaining = queue.drop(accepted)
                prefs.savePendingQueue(remaining)
                Logger.w("Partial flush: $accepted accepted, ${remaining.size} remain")
            } else {
                Logger.w("Queue flush failed; will retry later")
            }
        }
    }
}