package com.wifiscanner.portable

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import org.greenrobot.eventbus.EventBus
import java.io.BufferedReader
import java.net.Socket
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

class ScannerService : Service() {
    private var socket: Socket? = null
    private val devices = ConcurrentHashMap<String, DeviceStats>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var isRunning = false
    
    companion object {
        const val CHANNEL_ID = "RFScannerChannel"
        const val NOTIFICATION_ID = 1
        const val ROUTER_IP = "192.168.99.1"
        const val ROUTER_PORT = 9999
    }
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("RF Scanner Initializing..."))
        startLocationUpdates()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            Thread { runScanner() }.start()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RF Scanner Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WiFi device scanning service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RF Scanner Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e("Scanner", "Location permission not granted", e)
        }
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            currentLocation = locationResult.lastLocation
        }
    }
    
    private fun runScanner() {
        while (isRunning) {
            try {
                Log.i("Scanner", "Connecting to router at $ROUTER_IP:$ROUTER_PORT")
                updateNotification("Connecting to router...")
                
                socket = Socket(ROUTER_IP, ROUTER_PORT)
                socket?.soTimeout = 5000
                val reader = socket?.inputStream?.bufferedReader(Charset.forName("UTF-8"))
                
                updateNotification("Connected - Scanning...")
                Log.i("Scanner", "Connected to router")
                
                reader?.use {
                    while (isRunning) {
                        val line = it.readLine() ?: continue
                        try {
                            val frame = Gson().fromJson(line, Frame::class.java)
                            processFrame(frame)
                        } catch (e: Exception) {
                            Log.w("Scanner", "Failed to parse frame: $line", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Scanner", "Connection failed", e)
                updateNotification("Connection lost - Retrying...")
                Thread.sleep(5000)
            } finally {
                socket?.close()
            }
        }
    }
    
    private fun processFrame(frame: Frame) {
        devices.compute(frame.mac) { _, stats ->
            val updated = (stats ?: DeviceStats(frame.mac)).apply { 
                update(frame)
                
                currentLocation?.let { loc ->
                    gpsSamples.add(GpsSample(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        rssi = frame.rssi,
                        ts = System.currentTimeMillis()
                    ))
                    
                    if (gpsSamples.size > 100) {
                        gpsSamples.removeAt(0)
                    }
                }
            }
            updated
        }
        
        EventBus.getDefault().post(DevicesUpdate(devices.toMap()))
        updateNotification("Devices: ${devices.size} | Frames: ${devices.values.sumOf { it.rssiHistory.size }}")
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        socket?.close()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

data class Frame(
    val mac: String,
    val rssi: Int,
    val ch: Int,
    val ts: Double
)

data class DeviceStats(
    val mac: String,
    var rssiHistory: MutableList<Int> = mutableListOf(),
    val gpsSamples: MutableList<GpsSample> = mutableListOf()
) {
    val avgRssi: Int
        get() = if (rssiHistory.isEmpty()) 0 else rssiHistory.average().toInt()
    
    val lastSeen: Long
        get() = gpsSamples.lastOrNull()?.ts ?: 0
    
    fun update(frame: Frame) {
        rssiHistory.add(frame.rssi)
        if (rssiHistory.size > 50) {
            rssiHistory.removeAt(0)
        }
    }
    
    fun classify(): String {
        val oui = mac.substring(0, 8).uppercase()
        val vendor = OuiDatabase.lookup(oui)
        val stdDev = rssiHistory.stdDev()
        val pattern = if (stdDev < 5) "streaming" else "bursty"
        
        return when {
            vendor.contains("CAMERA", ignoreCase = true) || 
            vendor.matches(Regex(".*(?i)(wyze|hikvision|reolink).*")) -> "📹 Camera ($vendor)"
            vendor.contains("BULB", ignoreCase = true) || 
            vendor.contains("LIGHT", ignoreCase = true) -> "💡 Smart Bulb ($vendor)"
            vendor.matches(Regex(".*(?i)(philips hue).*")) -> "💡 Philips Hue"
            rssiHistory.size > 20 && pattern == "streaming" -> "🎥 Streaming Device"
            else -> "🔌 IoT Device ($vendor)"
        }
    }
}

data class GpsSample(
    val lat: Double,
    val lon: Double,
    val rssi: Int,
    val ts: Long
)

data class DevicesUpdate(val devices: Map<String, DeviceStats>)

fun List<Int>.stdDev(): Double {
    if (size < 2) return 0.0
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return kotlin.math.sqrt(variance)
}
