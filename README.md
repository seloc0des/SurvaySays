# WiFi Device Scanner - Portable RF Scanner

A portable WiFi device scanner that detects, classifies, and maps IoT devices using an OpenWrt router and Android phone.

## Project Structure

```
SurvaySays/
├── router/                    # OpenWrt router configuration
│   ├── sniffer               # Init script for WiFi monitoring
│   └── setup.sh              # Router setup automation
│
├── android/                   # Android application
│   ├── build.gradle          # App dependencies
│   ├── settings.gradle       # Project settings
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/wifiscanner/portable/
│       │   ├── MainActivity.kt         # Main UI and map
│       │   ├── ScannerService.kt       # Background scanner service
│       │   ├── DeviceListAdapter.kt    # RecyclerView adapter
│       │   ├── OuiDatabase.kt          # Device vendor lookup
│       │   └── KMLExporter.kt          # KML export utility
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml   # Main screen layout
│           │   └── item_device.xml     # Device list item
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── themes.xml
│
└── Complete Build Guide_ Portable WiFi Device Scanner.md
```

## Hardware Requirements

- OpenWrt compatible router with monitor mode support 
- Samsung WiFi phone (or any Android device with GPS)
- Ryobi battery with DC output (12V) (This is what I am using, you could definitely figure out your own power situation.)
- USB-C Ethernet adapter (recommended) (I am currently using the usb to ethernet adapter from my Nintendo Switch and a usb to a usb-c adapter to the Samsung device I am using)
- 3D printed case (optional, STL files not included)

## Software Requirements

### Router
- OpenWrt firmware
- Packages: tcpdump, aircrack-ng, netcat-openbsd, luci

### Android
- Android Studio (latest version)
- Android SDK 26+ (Android 8.0+)
- Google Maps API key

## Setup Instructions

### 1. Router Setup

```bash
cd router
chmod +x setup.sh
./setup.sh
```

Manual steps:
1. SSH into router: `ssh root@192.168.1.1`
2. Configure scanner network (192.168.99.1/24)
3. Create SSID "RFScanner" with password "rfscanner2026"
4. Copy and enable sniffer script
5. Reboot router

### 2. Android App Setup

1. Open Android Studio
2. Import the `android` directory as a project
3. Get a Google Maps API key from [Google Cloud Console](https://console.cloud.google.com/)
4. Add your API key to `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_ACTUAL_API_KEY_HERE" />
   ```
5. Build and install the app to your device

### 3. Usage

1. Power on the router using Ryobi battery
2. Connect phone to "RFScanner" WiFi network (or use USB-C Ethernet)
3. Launch the app and grant location/WiFi permissions
4. Start walking - the app will automatically detect and classify devices
5. View real-time map with device locations
6. Export data as KML for Google Earth

## Features

- **Real-time WiFi Monitoring**: Captures all WiFi frames in monitor mode
- **Device Classification**: Identifies cameras, bulbs, streaming devices, and IoT
- **GPS Mapping**: Plots devices on Google Maps with color-coded markers
- **OUI Lookup**: Recognizes major IoT vendors (Wyze, Ring, Nest, TP-Link, etc.)
- **KML Export**: Export scan data for visualization in Google Earth
- **Background Service**: Continues scanning even when app is minimized

## Device Colors on Map

- 🔴 Red: Security cameras
- 🟡 Yellow: Smart bulbs and lights
- 🟠 Orange: Streaming devices
- 🔵 Blue: Other IoT devices

## Troubleshooting

### No GPS Location
- Enable "High accuracy" mode in Location settings
- Ensure GPS permissions are granted

### No Device Data
- Check router connection: `nc 192.168.99.1 9999`
- Restart sniffer: `ssh root@192.168.99.1 '/etc/init.d/sniffer restart'`

### App Crashes
- Check logcat for errors
- Verify Google Maps API key is correct
- Ensure all permissions are granted

## Technical Details

### Data Flow
1. Router captures WiFi frames in monitor mode
2. tcpdump processes frames and extracts MAC, RSSI, channel
3. AWK script converts to JSON format
4. netcat streams JSON on port 9999
5. Android app connects and processes stream
6. Combines with GPS data for mapping

### Network Architecture
```
WiFi Devices → Router (monitor mode) → tcpdump → JSON stream → Android App → Map
                                                              ↓
                                                      GPS + Classification
```

## Battery Life
- Expected runtime: 4+ hours continuous scanning
- Ryobi 12V battery output provides stable power
- Monitor router temperature through case vents

## Security Notes
- Scanner network is isolated from main network
- No internet connection required for scanning
- All data stored locally on device
- WiFi-only phone recommended to avoid cellular interference

## License
Open source - modify and use as needed

## Credits
Based on the build guide from January 12, 2026
