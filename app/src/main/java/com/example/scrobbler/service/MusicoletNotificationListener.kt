package com.example.scrobbler.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.scrobbler.model.Track
import com.example.scrobbler.util.Logger
import com.example.scrobbler.util.Prefs

class MusicoletNotificationListener : NotificationListenerService() {

    companion object {
        const val MUSICOLET_PKG = "in.krosbits.musicolet"
    }

    private val stateManager = PlaybackStateManager()
    private lateinit var scrobbleEngine: ScrobbleEngine
    private val activeMusicoletKeys = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        scrobbleEngine = ScrobbleEngine(applicationContext)
        Logger.init(Prefs.get(applicationContext).debugLogging)
        Logger.i("NotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != MUSICOLET_PKG) return

        activeMusicoletKeys.add(sbn.key)
        Logger.d("Musicolet notification posted: ${sbn.key}")

        val notification = sbn.notification ?: return
        val (track, paused) = parseNotification(notification) ?: return

        if (!track.isValid) {
            Logger.w("Parsed invalid track from notification")
            return
        }

        ScrobblerForegroundService.startWith(
            applicationContext,
            "${track.artist} – ${track.title}"
        )

        val event = stateManager.onNotification(track, paused)
        Logger.d("SessionEvent: $event")

        when (event) {
            is SessionEvent.NewTrack -> {
                scrobbleEngine.sendNowPlaying(event.session)
                scrobbleEngine.flushOfflineQueue()
            }
            is SessionEvent.TrackChanged -> {
                event.scrobbleCandidate?.let { scrobbleEngine.scrobble(it) }
                scrobbleEngine.sendNowPlaying(event.newSession)
            }
            is SessionEvent.Resumed -> { }
            is SessionEvent.Paused  -> { }
            is SessionEvent.Ignored -> { }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != MUSICOLET_PKG) return

        activeMusicoletKeys.remove(sbn.key)
        Logger.d("Musicolet notification removed: ${sbn.key}")

        if (activeMusicoletKeys.isEmpty()) {
            Logger.i("All Musicolet notifications gone — finalising session")
            val pending = stateManager.onPlaybackStopped()
            pending?.let { scrobbleEngine.scrobble(it) }
            ScrobblerForegroundService.stop(applicationContext)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Logger.i("NotificationListener connected")
        try {
            activeNotifications
                ?.filter { it.packageName == MUSICOLET_PKG }
                ?.forEach { onNotificationPosted(it) }
        } catch (e: Exception) {
            Logger.e("Error recovering active notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Logger.w("NotificationListener disconnected — requesting rebind")
        requestRebind(
            android.content.ComponentName(applicationContext, MusicoletNotificationListener::class.java)
        )
    }

    private fun parseNotification(notification: Notification): Pair<Track, Boolean>? {
        val extras: Bundle = notification.extras ?: return null

        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val rawText  = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val rawSub   = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()

        Logger.d("Raw extras — title='$rawTitle' text='$rawText' sub='$rawSub'")

        if (rawTitle.isNullOrBlank()) return null

        var artist = ""
        var album  = ""

        when {
            rawText != null && rawText.contains(" – ") -> {
                val parts = rawText.split(" – ", limit = 2)
                artist = parts[0].trim()
                album  = parts[1].trim()
                if (!rawSub.isNullOrBlank()) album = rawSub
            }
            rawText != null && rawText.contains(" - ") -> {
                val parts = rawText.split(" - ", limit = 2)
                artist = parts[0].trim()
                album  = parts[1].trim()
                if (!rawSub.isNullOrBlank()) album = rawSub
            }
            else -> {
                artist = rawText?.trim() ?: ""
                album  = rawSub?.trim() ?: ""
            }
        }

        val paused = inferPausedState(notification)
        val track = Track(title = rawTitle, artist = artist, album = album)
        Logger.d("Parsed: artist='$artist' title='$rawTitle' album='$album' paused=$paused")
        return Pair(track, paused)
    }

    private fun inferPausedState(notification: Notification): Boolean {
        notification.actions?.forEach { action ->
            val label = action.title?.toString()?.lowercase() ?: return@forEach
            when {
                label.contains("pause") -> return false
                label.contains("play")  -> return true
            }
        }
        return false
    }
}