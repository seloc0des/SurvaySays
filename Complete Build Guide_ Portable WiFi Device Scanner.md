<img src="https://r2cdn.perplexity.ai/pplx-full-logo-primary-dark%402x.png" style="height:64px;margin-right:32px"/>

# Complete Build Guide: Portable WiFi Device Scanner + GPS Mapper (Updated)

**Date:** January 12, 2026
**Hardware:** OpenWrt router + **Samsung WiFi Phone** + **Ryobi battery DC** + **3D printed case**
**Capabilities:** Walk → detect all WiFi signals → classify devices → GPS heat map

## Hardware Bill of Materials (Your Setup)

| Item | Source | Status | Notes |
| :-- | :-- | :-- | :-- |
| OpenWrt router | You have | ✅ | Backup vacation config |
| **Samsung WiFi Phone** | You have | ✅ | GPS + WiFi perfect (no cellular = pure RF focus) |
| **Ryobi battery → DC output** | You have | ✅ | Powers router via original cord (stable 12V) |
| **3D printed case** | Your printer | ⏳ | Design below |
| **USB-C Ethernet adapter** | Amazon | \$10 | Wired phone ↔ router (recommended) |
| **Total new spend:** |  | **\$10** |  |

## 3D Printed Case Design Specs

```
Dimensions: 200x150x80mm (fits most routers)
Compartments:
- Router bay (vented sides)
- Ryobi battery slot (secure)
- Cable routing (DC cord + Ethernet)
- Antenna clearance (if external)
STL files: Fusion360 → "Portable Router Scanner Case"
Key features:
- Battery access door
- Phone mount (optional)
- Cable glands for clean wiring
```


## Phase 1: Router Setup (2 hours)

### Step 1.1: Backup \& Prep OpenWrt

```bash
ssh root@192.168.1.1
sysupgrade -b /tmp/backup-wifi-scanner-$(date +%Y%m%d).tar.gz
scp root@192.168.1.1:/tmp/backup*.tar.gz ~/Desktop/
opkg update
opkg install tcpdump kmod-b43 aircrack-ng luci luci-app-uhttpd netcat-openbsd usbutils
```


### Step 1.2: Dedicated Scanner Network

```
LuCI → Network → Interfaces → Add → "scanner" (192.168.99.1/24)
Wireless → Edit radio → SSID: "RFScanner" (WPA2: rfscanner2026)
Save → reboot
```


### Step 1.3: Test Monitor Mode

```bash
wifi down
wl phy_tempsense 0
wl monitor 1
ifconfig mon0 up
tcpdump -i mon0 -n -c 20
# ✅ See nearby WiFi (phones, neighbors, IoT)
```


### Step 1.4: Production JSON Streamer

`/etc/init.d/sniffer` (make executable):

```bash
#!/bin/sh /etc/rc.common
START=99
wl down && wl monitor 1 && ifconfig mon0 up
tcpdump -i mon0 -n -l -w - -s 128 not port 9999 2>/dev/null | \
awk '{
  ts=systime();
  mac=$0; match(mac, /([0-9a-f]{2}:){5}[0-9a-f]{2}/); mac=substr(mac, RSTART, RLENGTH);
  rssi=$0; match(rssi, /(-?[0-9]+)dB/); rssi=substr(rssi, RSTART, RLENGTH);
  ch=$0; match(ch, /channel[[:space:]]*([0-9]+)/); ch=substr(ch, RSTART+8, RLENGTH-8);
  if(mac!="") printf "{\"ts\":%.3f,\"mac\":\"%s\",\"rssi\":%s,\"ch\":%s}\n", ts, mac, rssi, ch
}' | nc -l 9999 &
```

```bash
chmod +x /etc/init.d/sniffer
/etc/init.d/sniffer enable
/etc/init.d/sniffer start
```

**Test:** Phone → "RFScanner" WiFi → `nc 192.168.99.1 9999`

## Phase 2: Samsung WiFi Phone App (4-6 hours)

### Step 2.1: Android Studio Project

```
New Project → Empty Activity (minSdk 26)
build.gradle (app):
dependencies {
    implementation 'com.google.android.gms:play-services-location:21.3.0'
    implementation 'com.google.android.gms:play-services-maps:18.2.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```


### Step 2.2: Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```


### Step 2.3: Core ScannerService.kt

```kotlin
class ScannerService : Service() {
    private lateinit var socket: Socket
    private val devices = ConcurrentHashMap<String, DeviceStats>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    override fun onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationClient(this)
        startForeground(1, createNotification("RF Scanner Active"))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread { runScanner() }.start()
        return START_STICKY
    }
    
    private fun runScanner() {
        try {
            // Wired or WiFi to router
            socket = Socket("192.168.99.1", 9999)
            socket.soTimeout = 5000
            val reader = socket.inputStream.bufferedReader(Charset.forName("UTF-8"))
            
            while (true) {
                val line = reader.readLine() ?: continue
                val frame = Gson().fromJson(line, Frame::class.java)
                devices.compute(frame.mac) { _, stats ->
                    (stats ?: DeviceStats(frame.mac)).apply { update(frame) }
                }
            }
        } catch (e: Exception) {
            Log.e("Scanner", "Connection failed", e)
            Thread.sleep(5000)
            runScanner()  // Retry
        }
    }
}

data class Frame(val mac: String, val rssi: Int, val ch: Int, val ts: Double)
data class DeviceStats(
    val mac: String,
    var rssiHistory: MutableList<Int> = mutableListOf(0),
    val gpsSamples: MutableList<GpsSample> = mutableListOf()
) {
    val avgRssi get() = rssiHistory.average().toInt()
    
    fun update(frame: Frame) {
        rssiHistory.add(frame.rssi)
        if (rssiHistory.size > 50) rssiHistory.removeAt(0)
    }
    
    fun classify(): String {
        val oui = mac.substring(0, 8).uppercase()
        val vendor = ouiDatabase[oui] ?: "Unknown"
        val pattern = if (rssiHistory.stdDev() < 5) "streaming" else "bursty"
        
        return when {
            vendor.contains("CAMERA", ignoreCase = true) || 
            vendor.contains("WYZE|Hikvision|Reolink") -> "📹 Camera ($vendor)"
            vendor.contains("BULB|LIGHT") -> "💡 Smart Bulb ($vendor)"
            rssiHistory.size > 20 && pattern == "streaming" -> "🎥 Streaming Device"
            else -> "🔌 IoT Device ($vendor)"
        }
    }
}

data class GpsSample(val lat: Double, val lon: Double, val rssi: Int, val ts: Long)
```


### Step 2.4: MainActivity + Map (MainActivity.kt)

```kotlin
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var adapter: DeviceListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Start scanner
        startForegroundService(Intent(this, ScannerService::class.java))
        
        recyclerView.adapter = adapter  // Live device list
        
        mapFragment.getMapAsync(this)
        requestPermissions()  // GPS/WiFi
    }
    
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMyLocationButtonEnabled = true
        
        // Live markers from ScannerService.devices
        EventBus.getDefault().register(this)
    }
    
    @Subscribe
    fun onDevicesUpdated(event: DevicesUpdate) {
        map.clear()
        event.devices.forEach { (mac, stats) ->
            stats.gpsSamples.lastOrNull()?.let { sample ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(sample.lat, sample.lon))
                        .title(stats.classify())
                        .icon(BitmapDescriptorFactory.defaultMarker(
                            when { stats.classify().contains("Camera") -> BitmapDescriptorFactory.HUE_RED }
                            else -> BitmapDescriptorFactory.HUE_BLUE }))
                )
            }
        }
    }
}
```


## Phase 3: OUI Database (assets/oui.json)

```json
{
  "00:1E:C2": "Wyze",
  "00:0E:58": "Hikvision", 
  "C4:4F:33": "Reolink",
  "A4:C1:38": "Philips Hue",
  "5C:CF:7F": "TP-Link Kasa"
}
```

**Full list:** `wget http://standards-oui.ieee.org/oui/oui.txt | grep -i camera > filtered.txt`

## Phase 4: Physical Build (2-3 hours print + assembly)

### Step 4.1: 3D Print Case

```
Fusion360 design:
- Outer: 220x160x90mm
- Router cavity: exact model dims + 2mm vent holes
- Ryobi battery slot: secure with clips
- DC cord pass-through (15mm hole)
- Ethernet gland (USB-C cable)
- Handle/grip top
Print: PETG, 0.2mm layers, 20% infill
```


### Step 4.2: Wiring

```
Ryobi DC output (12V?) → Router DC barrel jack (measure voltage!)
USB-C Ethernet: Router LAN → Samsung phone
Ventilation: case holes align with router vents
```


### Step 4.3: Power Test

```
1. Ryobi → router → boots OK?
2. Phone → "RFScanner" WiFi → nc 192.168.99.1 9999 → JSON?
3. Case closes → no overheating?
```


## Phase 5: Field Test Checklist

```
□ Case assembled, router stable on Ryobi DC
□ App installed, GPS/WiFi perms granted
□ Connect phone → RFScanner WiFi
□ Start scan → live device list appears
□ Walk 50m test path → GPS map populates
□ Export KML → opens in Google Earth
□ Battery runtime > 4hrs continuous
```


## Samsung WiFi Phone Optimizations

```
✅ GPS works fine (no cell = pure GNSS focus)
✅ Samsung DeX compatible (optional HDMI out to monitor)
✅ WiFi-only = perfect RF scanner (no cellular noise)
⚠️ Wired Ethernet via USB-C recommended (WiFi link stable)
```


## Troubleshooting

```
No GPS: Settings → Location → High accuracy ON
No JSON: /etc/init.d/sniffer restart
Weak signal: Case vents clear? Antenna unobstructed?
App crash: Check logcat for socket timeout
```

**Total build:** 10-12 hours + 3D print time
**First field test:** Walk your block → map all neighbor IoT!

**Need STL case file or full APK source repo?**
<span style="display:none">[^1][^10][^11][^12][^13][^14][^15][^2][^3][^4][^5][^6][^7][^8][^9]</span>

<div align="center">⁂</div>

[^1]: https://www.samsung.com/us/support/answer/ANS10001955/

[^2]: https://www.reddit.com/r/Kalilinux/comments/11oam2y/nethunter_phone_that_supports_monitor_mode/

[^3]: https://null-byte.wonderhowto.com/how-to/android-cyanogenmod-kernel-building-monitor-mode-any-android-device-with-wireless-adapter-0162943/

[^4]: https://github.com/kimocoder/qualcomm_android_monitor_mode

[^5]: https://xdaforums.com/t/monitor-mode-on-snapdragon-chipsets.4104437/

[^6]: https://www.samsung.com/latin_en/support/mobile-devices/how-to-activate-my-location-and-change-settings-for-location-permissions/

[^7]: https://www.reddit.com/r/ryobi/comments/16htbyv/if_i_just_want_to_power_my_internet_during_an/

[^8]: https://www.samsung.com/uk/support/apps-services/how-to-use-samsung-dex/

[^9]: https://www.reddit.com/r/explainlikeimfive/comments/1abkblv/eli5_why_does_a_phone_need_cell_signalwifi_for/

[^10]: https://www.ryobitools.com/products/46396035370

[^11]: https://www.youtube.com/watch?v=jKv5eGOTkVQ

[^12]: https://www.samsung.com/us/support/answer/ANS00080655/

[^13]: https://www.youtube.com/watch?v=QtTH-Y0XGfY

[^14]: https://insights.samsung.com/2024/08/26/the-beginners-guide-to-samsung-dex-13/

[^15]: https://www.bestbuy.com/site/searchpage.jsp?browsedCategory=pcmcat1661803373461\&id=pcat17071\&qp=features_facet%3DFeatures~GPS+Enabled\&st=pcmcat1661803373461_categoryid%24pcmcat305200050001

