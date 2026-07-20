package com.mobileagent.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mobileagent.R
import com.mobileagent.data.AgentLog
import com.mobileagent.data.LogType

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private val items = mutableListOf<AgentLog>()

    fun submitList(newItems: List<AgentLog>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addLog(log: AgentLog) {
        items.add(log)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val indicator = view.findViewById<View>(R.id.logIndicator)
        private val timestamp = view.findViewById<TextView>(R.id.tvLogTimestamp)
        private val tag = view.findViewById<TextView>(R.id.tvLogTag)
        private val message = view.findViewById<TextView>(R.id.tvLogMessage)

        fun bind(log: AgentLog) {
            timestamp.text = log.formattedTime
            tag.text = log.tag
            message.text = log.message

            val colorRes = when (log.type) {
                LogType.ACTION -> R.color.log_action
                LogType.RESULT -> R.color.log_result
                LogType.ERROR  -> R.color.log_error
                LogType.INFO   -> R.color.log_info
            }
            indicator.setBackgroundResource(colorRes)
            tag.setTextColor(tag.context.getColor(colorRes))
        }
    }
}