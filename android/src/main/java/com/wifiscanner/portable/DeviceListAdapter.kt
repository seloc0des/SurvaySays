package com.wifiscanner.portable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {
    private var devices = listOf<DeviceStats>()
    
    fun updateDevices(newDevices: List<DeviceStats>) {
        devices = newDevices.sortedByDescending { it.lastSeen }
        notifyDataSetChanged()
    }
    
    fun clearDevices() {
        devices = emptyList()
        notifyDataSetChanged()
    }
    
    fun getDevices(): List<DeviceStats> = devices
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }
    
    override fun getItemCount(): Int = devices.size
    
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvClassification: TextView = itemView.findViewById(R.id.tvClassification)
        private val tvMac: TextView = itemView.findViewById(R.id.tvMac)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvRssi)
        private val tvSamples: TextView = itemView.findViewById(R.id.tvSamples)
        
        fun bind(device: DeviceStats) {
            tvClassification.text = device.classify()
            tvMac.text = device.mac
            tvRssi.text = "${device.avgRssi} dBm"
            tvSamples.text = "${device.rssiHistory.size} samples | ${device.gpsSamples.size} GPS"
        }
    }
}
