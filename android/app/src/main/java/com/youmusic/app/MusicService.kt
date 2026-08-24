package com.youmusic.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import java.net.URL
import java.util.concurrent.Executors

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "youmusic_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE = "com.youmusic.app.action.UPDATE"
        const val ACTION_STOP = "com.youmusic.app.action.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ART_URL = "extra_art_url"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val artExecutor = Executors.newSingleThreadExecutor()
    private var currentArtUrl: String? = null
    private var currentArt: Bitmap? = null
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "YouMusicSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlaybackCommandBus.send(PlaybackCommandBus.Command.PLAY) }
                override fun onPause() { PlaybackCommandBus.send(PlaybackCommandBus.Command.PAUSE) }
                override fun onSkipToNext() { PlaybackCommandBus.send(PlaybackCommandBus.Command.NEXT) }
                override fun onSkipToPrevious() { PlaybackCommandBus.send(PlaybackCommandBus.Command.PREVIOUS) }
                override fun onStop() { PlaybackCommandBus.send(PlaybackCommandBus.Command.PAUSE) }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
                val artUrl = intent.getStringExtra(EXTRA_ART_URL)
                val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                val position = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                applyPlaybackState(position)
                loadArtAndPublish(title, artist, artUrl, duration)
            }
            ACTION_STOP -> stopPlaybackService()
        }
        return START_NOT_STICKY
    }

    private fun applyPlaybackState(positionMs: Long) {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, positionMs, 1f)
                .build()
        )
        mediaSession.isActive = true
    }

    private fun loadArtAndPublish(title: String, artist: String, artUrl: String?, durationMs: Long) {
        fun finish(art: Bitmap?) {
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                    .apply { if (art != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art) }
                    .build()
            )
            pushNotification(title, artist, art)
        }

        if (artUrl.isNullOrBlank()) {
            currentArt = null; currentArtUrl = null
            finish(null)
            return
        }
        if (artUrl == currentArtUrl && currentArt != null) {
            finish(currentArt)
            return
        }
        currentArtUrl = artUrl
        artExecutor.execute {
            val bmp = try {
                URL(artUrl).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
            currentArt = bmp
            finish(bmp)
        }
    }

    private fun pushNotification(title: String, artist: String, art: Bitmap?) {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)
            )
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(art)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            )
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (isPlaying) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            // Paused: behave like Spotify — notification stays visible and
            // controllable, but is no longer "ongoing"/undismissable, and
            // the service drops out of the foreground state so Android can
            // reclaim it if memory is needed.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopPlaybackService() {
        mediaSession.isActive = false
        mediaSession.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pemutaran Musik", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutaran lagu YouMusic"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession.release()
        artExecutor.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
