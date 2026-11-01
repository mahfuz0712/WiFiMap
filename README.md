# Wi-Fi Map

Sci-fi HUD styled **community Wi-Fi map** for Android 5.0 (API 21) and newer. Same design language as the Dialer / Browser apps.
Package `com.mux.android.wifimap` - Version `1.0.0`. Needs the **WiFiMap-Server** (separate project) for the shared-password database.

## What it does
- Scans nearby Wi-Fi and lists every access point with a big **SSID** and: security (OPEN / WEP / WPA / WPA2 / WPA3 / enterprise),
  **WPS on/off**, channel, **2.4 / 5 / 6 GHz**, width, Wi-Fi standard, signal, **router or hotspot (estimate)**, and the **brand** from the MAC address (IEEE OUI list bundled in `assets/oui.txt`).
- Checks the community database by **BSSID**: shows how many different passwords were shared and by how many people.
- Network page: all shared passwords, votes (worked / failed), copy, **connect** (Android 11+ system dialog, 10 suggestion, 9- classic), report, remove mine.
- **Scan a Wi-Fi QR code** (public places print one) and share its password in one tap.
- MINE tab: what you shared. SYS tab: developer info, update check (GitHub releases), settings, server address, rules and permissions.

## Rules (also shown in the app)
Not a hacking tool - passwords are only shared by people on purpose. No personal hotspots, no private networks without permission.
Owners can report "I am the owner" to have a password removed.

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). Set the server address in `AppInfo.API_BASE` (or in SYS).
CI: `.github/workflows/build.yml`.
