package ir.vipcall.ringer

import android.app.Activity
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class ContactPickerActivity : Activity() {

    private val all = mutableListOf<VipContact>()
    private var shown = listOf<VipContact>()
    private val checked = mutableSetOf<String>()
    private lateinit var adapter: ContactAdapter
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        val listView = findViewById<ListView>(R.id.listContacts)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val search = findViewById<EditText>(R.id.etSearch)
        btnSave = findViewById(R.id.btnSave)

        VipStore.getContacts(this).forEach { checked += it.id }
        adapter = ContactAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            val id = shown[pos].id
            if (!checked.remove(id)) checked += id
            adapter.notifyDataSetChanged()
            updateSaveButton()
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        btnSave.setOnClickListener { save() }
        updateSaveButton()

        Thread {
            val loaded = loadContacts()
            runOnUiThread {
                all.clear()
                all.addAll(loaded)
                progress.visibility = View.GONE
                applyFilter(search.text.toString())
            }
        }.start()
    }

    private fun loadContacts(): List<VipContact> {
        val map = LinkedHashMap<String, Pair<String, MutableList<String>>>()
        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val num = c.getString(2) ?: continue
                    val entry = map.getOrPut(id) { Pair(name, mutableListOf()) }
                    // حذف شماره‌های تکراری (مثل 0912… و ‎+98912…)
                    if (entry.second.none { PhoneUtil.key(it) == PhoneUtil.key(num) }) {
                        entry.second += num
                    }
                }
            }
        } catch (e: SecurityException) {
            runOnUiThread { Toast.makeText(this, R.string.need_contacts, Toast.LENGTH_LONG).show() }
        }
        return map.map { (id, p) -> VipContact(id, p.first.ifBlank { p.second.first() }, p.second) }
    }

    private fun applyFilter(q: String) {
        val query = q.trim()
        val qDigits = PhoneUtil.digits(query)
        shown = if (query.isEmpty()) all else all.filter { v ->
            v.name.contains(query, ignoreCase = true) ||
                    (qDigits.length >= 3 && v.numbers.any { PhoneUtil.digits(it).contains(qDigits) })
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateSaveButton() {
        btnSave.text = getString(R.string.save_count, checked.size)
    }

    private fun save() {
        val loadedIds = all.map { it.id }.toSet()
        // مخاطبینی که قبلاً ذخیره شده‌اند ولی دیگر در دفترچه نیستند، حفظ می‌شوند
        val kept = VipStore.getContacts(this).filter { it.id !in loadedIds && it.id in checked }
        val selected = all.filter { it.id in checked }
        VipStore.saveContacts(this, selected + kept)
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private inner class ContactAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_contact, parent, false)
            val item = shown[position]
            v.findViewById<TextView>(R.id.tvName).text = item.name
            v.findViewById<TextView>(R.id.tvNumbers).text = item.numbers.joinToString("   ")
            v.findViewById<CheckBox>(R.id.cb).isChecked = item.id in checked
            return v
        }
    }
}
