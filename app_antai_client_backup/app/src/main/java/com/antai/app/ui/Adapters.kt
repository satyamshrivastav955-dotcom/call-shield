package com.antai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.antai.app.data.local.CallLogEntity
import com.antai.app.data.local.ContactEntity
import com.antai.app.data.local.MessageEntity
import com.antai.app.data.local.ReportEntity
import com.antai.app.data.local.VerdictEntity
import com.antai.app.databinding.ItemContactBinding
import com.antai.app.databinding.ItemConversationBinding
import com.antai.app.databinding.ItemHistoryCallBinding
import com.antai.app.databinding.ItemHistoryReportBinding
import com.antai.app.databinding.ItemHistoryVerdictBinding
import com.antai.app.databinding.ItemMessageInBinding
import com.antai.app.databinding.ItemMessageOutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactsAdapter(
    private val onTap: (ContactEntity) -> Unit,
    private val onFlag: (ContactEntity) -> Unit,
) : RecyclerView.Adapter<ContactsAdapter.VH>() {
    private val items = mutableListOf<ContactEntity>()

    fun submit(list: List<ContactEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.tvName.text = c.label.ifBlank { c.displayName }
        holder.binding.tvPhone.text = c.phone
        holder.binding.tvTag.text = c.relationshipTag ?: ""
        holder.binding.tvTag.visibility = if (c.relationshipTag.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.binding.ivTrusted.visibility = if (c.isTrusted || c.linked) View.VISIBLE else View.GONE
        holder.binding.root.setOnClickListener { onTap(c) }
        holder.binding.root.setOnLongClickListener {
            onFlag(c)
            true
        }
    }
}

class ConversationsAdapter(
    private val onTap: (MessageEntity) -> Unit,
) : RecyclerView.Adapter<ConversationsAdapter.VH>() {
    private val items = mutableListOf<MessageEntity>()

    fun submit(list: List<MessageEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.binding.tvPeer.text = m.peerPhone
        holder.binding.tvPreview.text = m.body
        holder.binding.tvTime.text = fmtTime(m.createdAt)
        if (m.intercepted) holder.binding.tvPreview.text = "🛡 Held by antAI: ${m.body}"
        holder.binding.root.setOnClickListener { onTap(m) }
    }
}

class MessagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<MessageEntity>()

    fun submit(list: List<MessageEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class InVH(val binding: ItemMessageInBinding) : RecyclerView.ViewHolder(binding.root)
    class OutVH(val binding: ItemMessageOutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) = if (items[position].fromMe) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == 1)
            OutVH(ItemMessageOutBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else
            InVH(ItemMessageInBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = items[position]
        val body = if (m.intercepted && !m.fromMe)
            "🛡 Message held by antAI: ${m.body}" else m.body
        val shown = if (m.riskScore >= 70.0) "🚨 $body" else body
        when (holder) {
            is InVH -> {
                holder.binding.tvBody.text = shown
                holder.binding.tvMeta.text = fmtTime(m.createdAt)
            }
            is OutVH -> {
                holder.binding.tvBody.text = shown
                holder.binding.tvMeta.text = fmtTime(m.createdAt)
            }
        }
    }

    companion object {
        fun fmtTime(t: Long): String =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
    }
}

class VerdictHistoryAdapter : RecyclerView.Adapter<VerdictHistoryAdapter.VH>() {
    private val items = mutableListOf<VerdictEntity>()

    fun submit(list: List<VerdictEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemHistoryVerdictBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryVerdictBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        holder.binding.tvVerdict.text = v.verdictText.ifBlank { "No alert" }
        holder.binding.tvWhy.text = v.why
        holder.binding.tvMeta.text = "${v.kind} · ${v.band} · risk ${v.riskScore.toInt()}"
        val bg = when (v.band) {
            "critical" -> 0xFFFFE5E5.toInt()
            "verify" -> 0xFFFFF8E1.toInt()
            else -> 0xFFE8F5E9.toInt()
        }
        holder.binding.card.setCardBackgroundColor(bg)
    }
}

class ReportHistoryAdapter : RecyclerView.Adapter<ReportHistoryAdapter.VH>() {
    private val items = mutableListOf<ReportEntity>()
    var onTap: ((ReportEntity) -> Unit)? = null

    fun submit(list: List<ReportEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemHistoryReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.binding.tvTitle.text = r.title
        holder.binding.tvMeta.text = r.kind
        holder.binding.root.setOnClickListener { onTap?.invoke(r) }
    }
}

class CallHistoryAdapter : RecyclerView.Adapter<CallHistoryAdapter.VH>() {
    private val items = mutableListOf<CallLogEntity>()

    fun submit(list: List<CallLogEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemHistoryCallBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryCallBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.binding.tvPeer.text = c.peerName.ifBlank { c.peerPhone }
        holder.binding.tvMeta.text = "${c.kind} · ${c.status} · risk ${c.riskPeak.toInt()}"
    }
}

private fun fmtTime(t: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(t))