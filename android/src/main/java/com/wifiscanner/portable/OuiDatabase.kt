package com.wifiscanner.portable

object OuiDatabase {
    private val database = mapOf(
        "00:1E:C2" to "Wyze",
        "00:0E:58" to "Hikvision",
        "C4:4F:33" to "Reolink",
        "A4:C1:38" to "Philips Hue",
        "5C:CF:7F" to "TP-Link Kasa",
        "00:17:88" to "Philips",
        "B0:CE:18" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "D8:0D:17" to "TP-Link",
        "00:24:E4" to "LIFX",
        "D0:73:D5" to "LIFX",
        "34:97:F6" to "Ring",
        "AC:3B:77" to "Nest",
        "18:B4:30" to "Nest",
        "64:16:66" to "Nest",
        "00:0C:8A" to "Ecobee",
        "44:61:32" to "Ecobee",
        "00:18:56" to "Honeywell",
        "B4:E6:2D" to "Raspberry Pi",
        "DC:A6:32" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "28:CD:C1" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "00:04:20" to "Slim Devices (Logitech)",
        "E0:D5:5E" to "Shenzhen Orvibo",
        "70:B3:D5:67:90" to "Sricam"
    )
    
    fun lookup(oui: String): String {
        val normalized = oui.uppercase().take(8)
        return database[normalized] ?: "Unknown"
    }
}
