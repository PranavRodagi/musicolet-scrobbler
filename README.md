# Musicolet → Last.fm Scrobbler

A headless Android app that automatically scrobbles tracks from Musicolet to Last.fm in the background. No polling, no audio recording — purely notification-based detection.

## Features

- Detects playback from Musicolet via its notification
- Sends "Now Playing" to Last.fm instantly on track start
- Scrobbles tracks following Last.fm rules (≥50% played or ≥240 seconds)
- Handles pause/resume with accurate timing
- Queues failed scrobbles offline and retries automatically
- Silent foreground service — no annoying notifications
- Samsung One UI battery optimisation friendly

## Requirements

- Android 8.0+ (API 26)
- Musicolet music player
- Last.fm account + API key

## Setup

1. Install the APK
2. Open the app and follow the 3 steps:
   - Grant notification access
   - Grant battery optimisation exemption
   - Enter Last.fm API key, shared secret, username and password
3. Close the app — it runs entirely in the background from here

## Getting a Last.fm API Key

1. Go to https://www.last.fm/api/account/create
2. Fill in any app name and description
3. Copy the API key and shared secret into the app

## Tech Stack

- Kotlin
- NotificationListenerService
- OkHttp (Last.fm API calls)
- Coroutines
- Gson (offline scrobble queue)
- ViewBinding

## Notes

- Musicolet must have its notification enabled for detection to work
- On Samsung devices, manually add the app to Settings → Apps → Musicolet Scrobbler → Battery → Unrestricted
- Track duration is not always available in Musicolet's notification — when unknown, only the 240-second rule applies
