package app.kurenai.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background narration session. The animator feeds narration through the shared
 * MediaSession so lock-screen and Bluetooth controls work while the reader is
 * backgrounded. ExoPlayer is kept idle when only TTS is active.
 */
class NarrationService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel("narration", "Animated chapter narration", NotificationManager.IMPORTANCE_LOW),
            )
        }
        startForeground(
            7,
            NotificationCompat.Builder(this, "narration")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Kurenai narration")
                .setContentText("Ready to narrate your chapter")
                .setOngoing(false)
                .build(),
        )
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.repeatMode = Player.REPEAT_MODE_ALL
        }
        session = MediaSession.Builder(this, player!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
