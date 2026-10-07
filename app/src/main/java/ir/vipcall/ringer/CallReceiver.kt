package ir.vipcall.ringer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!VipStore.isEnabled(context)) return
                // در اندروید ۱۰+ این broadcast دو بار می‌آید؛ یک بار بدون شماره. آن را نادیده می‌گیریم.
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (number.isNullOrBlank()) return

                if (!isPhoneMuted(context)) return   // گوشی خودش زنگ می‌زند، کاری لازم نیست

                val vip = VipStore.findByNumber(context, number) ?: return
                RingController.start(context, vip.name)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> RingController.stop(context)
        }
    }

    private fun isPhoneMuted(c: Context): Boolean {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true
        if (am.getStreamVolume(AudioManager.STREAM_RING) == 0) return true
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val f = nm.currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
                f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }
}
