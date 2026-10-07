package ir.vipcall.ringer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * وقتی گوشی سایلنت/ویبره است، خود برنامه آهنگ زنگ پیش‌فرض گوشی را
 * روی کانال «زنگ هشدار» پخش می‌کند؛ این کانال تحت تأثیر حالت سایلنت نیست.
 * بعد از پاسخ دادن یا قطع تماس، همه‌چیز به حالت قبل برمی‌گردد.
 */
object RingController {
    const val CHANNEL_ID = "vip_ring"
    private const val NOTIF_ID = 1001

    private const val K_ACTIVE = "ring_active"
    private const val K_SAVED_ALARM_VOL = "saved_alarm_vol"
    private const val K_SAVED_FILTER = "saved_filter"

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    val isRinging: Boolean get() = player != null || vibrator != null

    @Synchronized
    fun start(ctx: Context, callerName: String) {
        val c = ctx.applicationContext
        if (isRinging) return

        val prefs = VipStore.prefs(c)
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ذخیره‌ی وضعیت فعلی برای برگرداندن بعد از تماس (حتی اگر پروسه بسته شود)
        if (!prefs.getBoolean(K_ACTIVE, false)) {
            prefs.edit()
                .putBoolean(K_ACTIVE, true)
                .putInt(K_SAVED_ALARM_VOL, am.getStreamVolume(AudioManager.STREAM_ALARM))
                .putInt(K_SAVED_FILTER, -1)
                .commit()
        }

        // اگر «مزاحم نشوید» روشن است و دسترسی داریم، موقتاً خاموشش کن
        try {
            val filter = nm.currentInterruptionFilter
            if (nm.isNotificationPolicyAccessGranted &&
                filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                prefs.edit().putInt(K_SAVED_FILTER, filter).commit()
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (_: Exception) {
        }

        // بلندی صدا
        try {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val vol = max(1, (maxVol * VipStore.volume(c) / 100.0).roundToInt())
            am.setStreamVolume(AudioManager.STREAM_ALARM, vol, 0)
        } catch (_: Exception) {
        }

        player = createPlayer(c)
        player?.start()

        if (VipStore.vibrate(c)) startVibration(c)

        showNotification(c, callerName)
    }

    private fun createPlayer(c: Context): MediaPlayer? {
        val candidates = listOfNotNull<Uri>(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(c, uri)
                mp.isLooping = true
                mp.prepare()
                return mp
            } catch (_: Exception) {
                mp.release()
            }
        }
        return null
    }

    private fun startVibration(c: Context) {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (v == null || !v.hasVibrator()) return
        val pattern = longArrayOf(0, 1000, 800)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
            vibrator = v
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stop(ctx: Context) {
        val c = ctx.applicationContext
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null

        vibrator?.cancel()
        vibrator = null

        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)

        // برگرداندن تنظیمات قبلی
        val prefs = VipStore.prefs(c)
        if (prefs.getBoolean(K_ACTIVE, false)) {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    prefs.getInt(K_SAVED_ALARM_VOL, am.getStreamVolume(AudioManager.STREAM_ALARM)),
                    0
                )
            } catch (_: Exception) {
            }
            val savedFilter = prefs.getInt(K_SAVED_FILTER, -1)
            if (savedFilter > 0) {
                try {
                    if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(savedFilter)
                } catch (_: Exception) {
                }
            }
            prefs.edit().putBoolean(K_ACTIVE, false).putInt(K_SAVED_FILTER, -1).commit()
        }
    }

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    c.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                ch.setSound(null, null)
                ch.enableVibration(false)
                ch.setBypassDnd(true)
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun showNotification(c: Context, callerName: String) {
        try {
            ensureChannel(c)
            val stopIntent = PendingIntent.getBroadcast(
                c, 0, Intent(c, StopReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val openIntent = PendingIntent.getActivity(
                c, 1, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(c, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH)
            }
            b.setSmallIcon(R.drawable.ic_stat_ring)
                .setContentTitle(c.getString(R.string.notif_title, callerName))
                .setContentText(c.getString(R.string.notif_text))
                .setContentIntent(openIntent)
                .setDeleteIntent(stopIntent)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
            @Suppress("DEPRECATION")
            b.addAction(0, c.getString(R.string.notif_stop), stopIntent)
            c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, b.build())
        } catch (_: Exception) {
            // اگر اجازه‌ی اعلان داده نشده باشد، زنگ بدون اعلان ادامه پیدا می‌کند
        }
    }
}
