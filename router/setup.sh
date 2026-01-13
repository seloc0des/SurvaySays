#!/bin/bash
# OpenWrt Router Setup Script for WiFi Device Scanner

echo "=== WiFi Device Scanner Router Setup ==="
echo ""
echo "Step 1: Backing up current configuration..."
ssh root@192.168.1.1 "sysupgrade -b /tmp/backup-wifi-scanner-\$(date +%Y%m%d).tar.gz"
scp root@192.168.1.1:/tmp/backup*.tar.gz ~/Desktop/

echo ""
echo "Step 2: Installing required packages..."
ssh root@192.168.1.1 << 'EOF'
opkg update
opkg install tcpdump kmod-b43 aircrack-ng luci luci-app-uhttpd netcat-openbsd usbutils
EOF

echo ""
echo "Step 3: Copying sniffer script..."
scp sniffer root@192.168.1.1:/etc/init.d/sniffer

echo ""
echo "Step 4: Enabling sniffer service..."
ssh root@192.168.1.1 << 'EOF'
chmod +x /etc/init.d/sniffer
/etc/init.d/sniffer enable
EOF

echo ""
echo "=== Setup Complete ==="
echo "Next steps:"
echo "1. Configure scanner network in LuCI (192.168.99.1/24)"
echo "2. Create wireless SSID 'RFScanner' with WPA2 password 'rfscanner2026'"
echo "3. Reboot router"
echo "4. Start sniffer: ssh root@192.168.1.1 '/etc/init.d/sniffer start'"
