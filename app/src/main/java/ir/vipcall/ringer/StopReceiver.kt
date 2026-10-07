package ir.vipcall.ringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** دکمه‌ی «قطع صدا» در اعلان */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RingController.stop(context)
    }
}
