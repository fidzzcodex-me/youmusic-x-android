package com.youmusic.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * YouMusic is a native wrapper around the deployed web app. Everything —
 * theme, features, offline downloads, resume-on-refresh, etc. — lives in
 * the website itself (same HTML/CSS/JS as the browser version). This
 * Activity hosts it full-screen as a real Android app, and bridges it to
 * a Spotify-style background playback notification via [PlayerBridge]
 * and [MusicService].
 *
 * Change APP_URL below if the deployment domain ever changes.
 */
class MainActivity : ComponentActivity(), PlaybackCommandBus.Listener {

    private lateinit var webView: WebView

    @Volatile
    private var isMusicPlaying = false

    companion object {
        private const val APP_URL = "https://youmusic-x.vercel.app/"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        requestNotificationPermissionIfNeeded()

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Songs should be able to start playing without an extra tap.
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Lets the web app tell Android when a song starts/changes/pauses,
        // so MusicService can show/update the "now playing" notification.
        webView.addJavascriptInterface(
            PlayerBridge(applicationContext) { playing -> isMusicPlaying = playing },
            "AndroidPlayer"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return false
                // Keep normal in-app navigation inside the WebView. Only
                // hand off to an external browser/app for links that point
                // somewhere outside the app's own domain.
                return if (host == Uri.parse(APP_URL).host) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) { /* no app can handle it — ignore */ }
                    true
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(APP_URL)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Notification button (or Bluetooth/headset media button) was pressed
    // in MusicService — forward it into the web page's own player so the
    // real <audio> element actually plays/pauses/skips.
    override fun onCommand(command: PlaybackCommandBus.Command) {
        val js = when (command) {
            PlaybackCommandBus.Command.PLAY,
            PlaybackCommandBus.Command.PAUSE,
            PlaybackCommandBus.Command.TOGGLE ->
                "window.__nativeMediaCommand && window.__nativeMediaCommand('toggle')"
            PlaybackCommandBus.Command.NEXT ->
                "window.__nativeMediaCommand && window.__nativeMediaCommand('next')"
            PlaybackCommandBus.Command.PREVIOUS ->
                "window.__nativeMediaCommand && window.__nativeMediaCommand('previous')"
        }
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onStart() {
        super.onStart()
        PlaybackCommandBus.register(this)
    }

    override fun onStop() {
        PlaybackCommandBus.unregister(this)
        super.onStop()
    }

    override fun onPause() {
        // Only pause the WebView's own rendering/processing when nothing
        // is playing — pausing it while music is active would freeze the
        // page's JS/audio too, breaking exactly the "keeps playing in the
        // background" behavior this app is meant to have.
        if (!isMusicPlaying) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        if (isFinishing) {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            })
        }
        webView.destroy()
        super.onDestroy()
    }
}
