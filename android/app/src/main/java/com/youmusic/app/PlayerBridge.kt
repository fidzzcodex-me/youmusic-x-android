package com.youmusic.app

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat

/**
 * Bridge object added to the WebView as `window.AndroidPlayer`. The web
 * app calls these methods whenever a song starts/stops/changes, and this
 * forwards the info into [MusicService] so it can show/update the
 * Spotify-style "now playing" notification.
 *
 * All methods are called from the WebView's JS thread, so they only do
 * cheap work (build + send an Intent) — no UI/WebView access here.
 */
class PlayerBridge(
    private val context: Context,
    private val onPlayingChanged: (Boolean) -> Unit
) {

    @JavascriptInterface
    fun onPlaybackUpdate(
        title: String,
        artist: String,
        artUrl: String?,
        durationMs: Long,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        onPlayingChanged(isPlaying)
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_UPDATE
            putExtra(MusicService.EXTRA_TITLE, title)
            putExtra(MusicService.EXTRA_ARTIST, artist)
            putExtra(MusicService.EXTRA_ART_URL, artUrl)
            putExtra(MusicService.EXTRA_DURATION_MS, durationMs)
            putExtra(MusicService.EXTRA_POSITION_MS, positionMs)
            putExtra(MusicService.EXTRA_IS_PLAYING, isPlaying)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    @JavascriptInterface
    fun onPlaybackStopped() {
        onPlayingChanged(false)
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_STOP
        }
        context.startService(intent)
    }
}
