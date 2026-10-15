package com.glass.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class Row(
    val name: String,
    val sub: String,
    val number: String,
    val missed: Boolean = false
)

object CallHelper {
    fun call(context: Context, number: String) {
        if (number.isBlank()) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Toast.makeText(context, R.string.call_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
    }
}

class RowAdapter(private val onClick: (Row) -> Unit) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var items: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.avatar)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
        val btn: ImageView = v.findViewById(R.id.btn_call)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        val ctx = h.itemView.context
        h.name.text = r.name
        h.name.setTextColor(
            ContextCompat.getColor(ctx, if (r.missed) R.color.danger_red else R.color.text_primary)
        )
        h.sub.text = r.sub
        h.sub.visibility = if (r.sub.isBlank()) View.GONE else View.VISIBLE
        val first = r.name.trim().firstOrNull { it.isLetter() }
        h.avatar.text = first?.uppercaseChar()?.toString() ?: "#"
        h.btn.visibility = if (r.number.isBlank()) View.GONE else View.VISIBLE
        h.itemView.setOnClickListener { onClick(r) }
        h.btn.setOnClickListener { onClick(r) }
    }
}

abstract class BaseListFragment : Fragment(R.layout.fragment_list) {

    private lateinit var adapter: RowAdapter

    protected abstract val titleRes: Int
    protected open val emptyRes: Int = R.string.no_calls
    protected open val permission: String? = null
    protected open val showFab: Boolean = false
    protected abstract fun load(): List<Row>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.list_title).setText(titleRes)
        adapter = RowAdapter { CallHelper.call(requireContext(), it.number) }
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        val fab = view.findViewById<ImageView>(R.id.fab)
        if (showFab) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                startActivity(Intent(requireContext(), DialpadActivity::class.java))
            }
        }
        refresh(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refresh(it) }
    }

    private fun refresh(view: View) {
        val empty = view.findViewById<TextView>(R.id.empty)
        val p = permission
        val granted = p == null ||
            ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            adapter.submit(emptyList())
            empty.setText(R.string.need_permission)
            empty.visibility = View.VISIBLE
            return
        }
        val rows = try {
            load()
        } catch (e: Exception) {
            emptyList()
        }
        adapter.submit(rows)
        empty.setText(emptyRes)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }
}

class CallsFragment : BaseListFragment() {
    override val titleRes = R.string.tab_calls
    override val emptyRes = R.string.no_calls
    override val permission = Manifest.permission.READ_CALL_LOG
    override val showFab = true

    override fun load(): List<Row> {
        val ctx = requireContext()
        val out = ArrayList<Row>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 200) {
                val number = c.getString(0) ?: ""
                val cached = c.getString(1)
                val type = c.getInt(2)
                val date = c.getLong(3)
                val label = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> getString(R.string.call_incoming)
                    CallLog.Calls.OUTGOING_TYPE -> getString(R.string.call_outgoing)
                    CallLog.Calls.MISSED_TYPE -> getString(R.string.call_missed)
                    CallLog.Calls.REJECTED_TYPE -> getString(R.string.call_rejected)
                    else -> ""
                }
                val whenText = DateUtils.formatDateTime(
                    ctx, date,
                    DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
                )
                val name = when {
                    !cached.isNullOrBlank() -> cached
                    number.isBlank() -> getString(R.string.private_number)
                    else -> number
                }
                out.add(Row(name, "$label · $whenText", number, type == CallLog.Calls.MISSED_TYPE))
            }
        }
        return out
    }
}

class ContactsFragment : BaseListFragment() {
    override val titleRes = R.string.tab_contacts
    override val emptyRes = R.string.no_contacts
    override val permission = Manifest.permission.READ_CONTACTS

    override fun load(): List<Row> {
        val out = ArrayList<Row>()
        val seen = HashSet<String>()
        val phone = ContactsContract.CommonDataKinds.Phone
        requireContext().contentResolver.query(
            phone.CONTENT_URI,
            arrayOf(phone.DISPLAY_NAME, phone.NUMBER),
            null, null,
            "${phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: ""
                val number = c.getString(1) ?: ""
                if (number.isBlank()) continue
                if (!seen.add(name + number.filter { it.isDigit() })) continue
                out.add(Row(if (name.isBlank()) number else name, number, number))
            }
        }
        return out
    }
}

class SettingsFragment : BaseListFragment() {
    override val titleRes = R.string.tab_settings

    override fun load(): List<Row> = listOf(
        Row("Glass Dialer", "الإصدار 0.2", ""),
        Row("التطبيق الافتراضي للاتصال", "هيتفعّل في الدفعة الجاية مع شاشة المكالمة", "")
    )
}
