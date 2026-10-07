package ir.vipcall.ringer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var swEnabled: Switch
    private lateinit var swVibrate: Switch
    private lateinit var seekVolume: SeekBar
    private lateinit var tvVolume: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnPerms: Button
    private lateinit var btnDnd: Button
    private lateinit var btnBattery: Button
    private lateinit var btnTest: Button
    private lateinit var listVip: LinearLayout
    private lateinit var tvEmpty: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val runtimePerms: Array<String>
        get() {
            val list = mutableListOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
            return list.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swEnabled = findViewById(R.id.swEnabled)
        swVibrate = findViewById(R.id.swVibrate)
        seekVolume = findViewById(R.id.seekVolume)
        tvVolume = findViewById(R.id.tvVolume)
        tvStatus = findViewById(R.id.tvStatus)
        btnPerms = findViewById(R.id.btnPerms)
        btnDnd = findViewById(R.id.btnDnd)
        btnBattery = findViewById(R.id.btnBattery)
        btnTest = findViewById(R.id.btnTest)
        listVip = findViewById(R.id.listVip)
        tvEmpty = findViewById(R.id.tvEmpty)

        RingController.ensureChannel(this)

        swEnabled.isChecked = VipStore.isEnabled(this)
        swEnabled.setOnCheckedChangeListener { _, b -> VipStore.setEnabled(this, b) }

        swVibrate.isChecked = VipStore.vibrate(this)
        swVibrate.setOnCheckedChangeListener { _, b -> VipStore.setVibrate(this, b) }

        // SeekBar از ۰ تا ۹ ← ۱۰٪ تا ۱۰۰٪
        seekVolume.max = 9
        seekVolume.progress = VipStore.volume(this) / 10 - 1
        updateVolumeLabel()
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                VipStore.setVolume(this@MainActivity, (p + 1) * 10)
                updateVolumeLabel()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnPerms.setOnClickListener { requestPermissions(runtimePerms, 1) }

        btnDnd.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        btnBattery.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        btnTest.setOnClickListener {
            if (RingController.isRinging) {
                RingController.stop(this)
                btnTest.setText(R.string.test)
            } else {
                RingController.start(this, getString(R.string.test_name))
                btnTest.setText(R.string.test_stop)
                handler.postDelayed({
                    RingController.stop(this)
                    btnTest.setText(R.string.test)
                }, 6000)
            }
        }

        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(runtimePerms, 2)
            } else {
                startActivity(Intent(this, ContactPickerActivity::class.java))
            }
        }

        if (runtimePerms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(runtimePerms, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshList()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refreshStatus()
        if (code == 2 && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, ContactPickerActivity::class.java))
        }
    }

    private fun updateVolumeLabel() {
        tvVolume.text = getString(R.string.volume_label, VipStore.volume(this))
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun refreshStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val dndOk = nm.isNotificationPolicyAccessGranted
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val runtimeOk = runtimePerms.all { granted(it) }

        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        val sb = StringBuilder()
        sb.append(mark(granted(Manifest.permission.READ_CONTACTS))).append("  ").append(getString(R.string.perm_contacts)).append('\n')
        sb.append(mark(granted(Manifest.permission.READ_PHONE_STATE))).append("  ").append(getString(R.string.perm_phone)).append('\n')
        sb.append(mark(granted(Manifest.permission.READ_CALL_LOG))).append("  ").append(getString(R.string.perm_calllog)).append('\n')
        if (Build.VERSION.SDK_INT >= 33) {
            sb.append(mark(granted(Manifest.permission.POST_NOTIFICATIONS))).append("  ").append(getString(R.string.perm_notif)).append('\n')
        }
        sb.append(mark(dndOk)).append("  ").append(getString(R.string.perm_dnd)).append('\n')
        sb.append(mark(batteryOk)).append("  ").append(getString(R.string.perm_battery))
        tvStatus.text = sb.toString()

        btnPerms.visibility = if (runtimeOk) View.GONE else View.VISIBLE
        btnDnd.visibility = if (dndOk) View.GONE else View.VISIBLE
        btnBattery.visibility = if (batteryOk) View.GONE else View.VISIBLE
    }

    private fun refreshList() {
        listVip.removeAllViews()
        val contacts = VipStore.getContacts(this)
        tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
        for (v in contacts) {
            val row = layoutInflater.inflate(R.layout.item_vip, listVip, false)
            row.findViewById<TextView>(R.id.tvName).text = v.name
            row.findViewById<TextView>(R.id.tvNumbers).text = v.numbers.joinToString("   ")
            row.findViewById<Button>(R.id.btnRemove).setOnClickListener {
                VipStore.removeContact(this, v.id)
                Toast.makeText(this, getString(R.string.removed, v.name), Toast.LENGTH_SHORT).show()
                refreshList()
            }
            listVip.addView(row)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
