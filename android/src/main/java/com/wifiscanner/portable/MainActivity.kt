package com.wifiscanner.portable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.wifiscanner.portable.databinding.ActivityMainBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private lateinit var adapter: DeviceListAdapter
    private val markers = mutableMapOf<String, Marker>()
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupMap()
        
        if (checkPermissions()) {
            startScanner()
        } else {
            requestPermissions()
        }
        
        binding.btnExport.setOnClickListener {
            exportToKML()
        }
        
        binding.btnClear.setOnClickListener {
            adapter.clearDevices()
            markers.values.forEach { it.remove() }
            markers.clear()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = DeviceListAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = true
        map.uiSettings.isZoomControlsEnabled = true
        
        try {
            map.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        
        val defaultLocation = LatLng(37.7749, -122.4194)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScanner()
            } else {
                Toast.makeText(this, "Permissions required for scanning", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startScanner() {
        val intent = Intent(this, ScannerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }
    
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDevicesUpdated(event: DevicesUpdate) {
        adapter.updateDevices(event.devices.values.toList())
        updateMap(event.devices)
        binding.tvDeviceCount.text = "Devices: ${event.devices.size}"
    }
    
    private fun updateMap(devices: Map<String, DeviceStats>) {
        devices.forEach { (mac, stats) ->
            stats.gpsSamples.lastOrNull()?.let { sample ->
                val position = LatLng(sample.lat, sample.lon)
                val classification = stats.classify()
                
                if (markers.containsKey(mac)) {
                    markers[mac]?.position = position
                } else {
                    val color = when {
                        classification.contains("Camera") -> BitmapDescriptorFactory.HUE_RED
                        classification.contains("Bulb") || classification.contains("Light") -> 
                            BitmapDescriptorFactory.HUE_YELLOW
                        classification.contains("Streaming") -> BitmapDescriptorFactory.HUE_ORANGE
                        else -> BitmapDescriptorFactory.HUE_BLUE
                    }
                    
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(classification)
                            .snippet("MAC: $mac | RSSI: ${stats.avgRssi} dBm")
                            .icon(BitmapDescriptorFactory.defaultMarker(color))
                    )
                    marker?.let { markers[mac] = it }
                }
            }
        }
        
        if (devices.isNotEmpty() && markers.isNotEmpty()) {
            val bounds = LatLngBounds.builder()
            markers.values.forEach { bounds.include(it.position) }
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun exportToKML() {
        val devices = adapter.getDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "No devices to export", Toast.LENGTH_SHORT).show()
            return
        }
        
        val kml = KMLExporter.export(devices)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.google-earth.kml+xml"
            putExtra(Intent.EXTRA_TEXT, kml)
            putExtra(Intent.EXTRA_SUBJECT, "WiFi Device Scanner Data")
        }
        startActivity(Intent.createChooser(intent, "Export KML"))
        
        Toast.makeText(this, "Exported ${devices.size} devices", Toast.LENGTH_SHORT).show()
    }
}
