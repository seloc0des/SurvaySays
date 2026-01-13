package com.wifiscanner.portable

import java.text.SimpleDateFormat
import java.util.*

object KMLExporter {
    fun export(devices: List<DeviceStats>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        
        val kml = StringBuilder()
        kml.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        kml.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append("\n")
        kml.append("<Document>").append("\n")
        kml.append("<name>WiFi Device Scanner Data</name>").append("\n")
        kml.append("<description>Generated on ${dateFormat.format(Date())}</description>").append("\n")
        
        devices.forEach { device ->
            device.gpsSamples.forEach { sample ->
                kml.append("<Placemark>").append("\n")
                kml.append("<name>${escapeXml(device.classify())}</name>").append("\n")
                kml.append("<description>").append("\n")
                kml.append("MAC: ${device.mac}\n")
                kml.append("RSSI: ${sample.rssi} dBm\n")
                kml.append("Time: ${dateFormat.format(Date(sample.ts))}\n")
                kml.append("</description>").append("\n")
                kml.append("<Point>").append("\n")
                kml.append("<coordinates>${sample.lon},${sample.lat},0</coordinates>").append("\n")
                kml.append("</Point>").append("\n")
                kml.append("</Placemark>").append("\n")
            }
        }
        
        kml.append("</Document>").append("\n")
        kml.append("</kml>")
        
        return kml.toString()
    }
    
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
