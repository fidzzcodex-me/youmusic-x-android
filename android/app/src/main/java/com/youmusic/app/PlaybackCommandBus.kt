package com.youmusic.app

/**
 * Same-process pub/sub between [MusicService] (owns the notification +
 * MediaSession, so it receives button taps) and [MainActivity] (owns the
 * WebView, so it's the only thing that can actually tell the page's
 * <audio> element to play/pause/skip).
 *
 * Kept deliberately tiny instead of pulling in LocalBroadcastManager/AIDL —
 * service and activity always run in the same process here.
 */
object PlaybackCommandBus {

    enum class Command { PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS }

    fun interface Listener {
        fun onCommand(command: Command)
    }

    private var listener: Listener? = null

    fun register(listener: Listener) {
        this.listener = listener
    }

    fun unregister(listener: Listener) {
        if (this.listener === listener) this.listener = null
    }

    fun send(command: Command) {
        listener?.onCommand(command)
    }
}
