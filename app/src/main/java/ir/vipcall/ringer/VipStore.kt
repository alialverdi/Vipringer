package ir.vipcall.ringer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class VipContact(val id: String, val name: String, val numbers: List<String>)

object PhoneUtil {
    /** فقط ارقام (ارقام فارسی/عربی هم به لاتین تبدیل می‌شوند) */
    fun digits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (Character.isDigit(ch)) sb.append(Character.getNumericValue(ch))
        }
        return sb.toString()
    }

    /**
     * کلید مقایسه: ۱۰ رقم آخر شماره.
     * به این ترتیب 09121234567 و +989121234567 و 00989121234567 یکی حساب می‌شوند.
     */
    fun key(s: String): String = digits(s).takeLast(10)
}

object VipStore {
    private const val PREFS = "vip_prefs"
    private const val K_CONTACTS = "contacts"
    private const val K_ENABLED = "enabled"
    private const val K_VOLUME = "volume"
    private const val K_VIBRATE = "vibrate"

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getContacts(c: Context): List<VipContact> {
        val raw = prefs(c).getString(K_CONTACTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val nums = o.getJSONArray("numbers")
                VipContact(
                    o.getString("id"),
                    o.getString("name"),
                    (0 until nums.length()).map { nums.getString(it) }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveContacts(c: Context, list: List<VipContact>) {
        val arr = JSONArray()
        for (v in list) {
            val o = JSONObject()
            o.put("id", v.id)
            o.put("name", v.name)
            o.put("numbers", JSONArray(v.numbers))
            arr.put(o)
        }
        prefs(c).edit().putString(K_CONTACTS, arr.toString()).apply()
    }

    fun removeContact(c: Context, id: String) {
        saveContacts(c, getContacts(c).filter { it.id != id })
    }

    fun findByNumber(c: Context, number: String): VipContact? {
        val k = PhoneUtil.key(number)
        if (k.length < 6) return null
        return getContacts(c).firstOrNull { v -> v.numbers.any { PhoneUtil.key(it) == k } }
    }

    fun isEnabled(c: Context) = prefs(c).getBoolean(K_ENABLED, true)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(K_ENABLED, v).apply()

    /** بلندی صدای زنگ ویژه، ۱۰ تا ۱۰۰ درصد */
    fun volume(c: Context) = prefs(c).getInt(K_VOLUME, 100)
    fun setVolume(c: Context, v: Int) = prefs(c).edit().putInt(K_VOLUME, v).apply()

    fun vibrate(c: Context) = prefs(c).getBoolean(K_VIBRATE, true)
    fun setVibrate(c: Context, v: Boolean) = prefs(c).edit().putBoolean(K_VIBRATE, v).apply()
}
