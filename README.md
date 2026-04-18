# Network Metrics SDK

[![Release](https://img.shields.io/github/v/release/kevindupas/android-network-metrics-sdk)](https://github.com/kevindupas/android-network-metrics-sdk/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/Android-API%2026%2B-green?logo=android)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)](https://kotlinlang.org)

Android SDK for **continuous, passive mobile network quality measurement**. Runs as a `ForegroundService` with `START_STICKY` — collects data 24/7 without requiring the user to open any app.

## What it measures

| Category | Metrics |
|----------|---------|
| **Speed** | Download (Mbps), Upload (Mbps), Latency (ms), Jitter (ms), Loaded Latency (ms) |
| **Packet Loss** | UDP loss % — automatic TCP fallback if carrier blocks UDP |
| **Video Streaming** | HLS startup time, rebuffer count, rebuffer duration, avg bitrate |
| **Social Media Latency** | TTFB to WhatsApp, Instagram, YouTube, TikTok, X, Facebook |
| **Radio Signal** | RSRP, RSRQ, SINR, RSSI, CQI, Cell ID (CI), PCI, TAC, EARFCN |
| **Network Context** | ISP, ASN, IP address, Cloudflare PoP, IP version |
| **Location** | GPS lat/lon, accuracy, altitude, speed (FusedLocationProvider passive) |
| **Device** | Manufacturer, model, OS version, SIM operator, MCC/MNC, battery |
| **Quality Scores** | Streaming / Gaming / RTC quality (0–100, Poor/Core/Excellent) |
| **MOS** | Mean Opinion Score — ITU-T G.107 voice quality estimate |

---

## Requirements

- **Android API 26+** (Android 8.0 Oreo)
- **Kotlin 1.9+**
- Runtime permissions: `ACCESS_FINE_LOCATION` + `READ_PHONE_STATE`

---

## Installation

### JitPack (recommended)

Add JitPack to your project `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.kevindupas:android-network-metrics-sdk:1.0.1")
}
```

### AAR (manual)

Download `library-release.aar` from the [latest release](https://github.com/kevindupas/android-network-metrics-sdk/releases/latest) and place it in your `libs/` folder:

```kotlin
dependencies {
    implementation(files("libs/network-metrics-sdk-1.0.1.aar"))
    // Required transitive dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

---

## Android Manifest

The SDK declares all required permissions and the service automatically via manifest merging. If you need to declare them explicitly in your host app:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_BASIC_PHONE_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

> **Why `READ_PHONE_STATE`?** This permission is required to access `TelephonyDisplayInfo` (5G NSA detection), `getDataNetworkType()`, and `getAllCellInfo()` (Cell ID, RSRP, RSRQ, SINR). `READ_BASIC_PHONE_STATE` alone is insufficient for these APIs on all Android versions.

---

## Quick Start

### 1. Initialise and start

```kotlin
// In Application.onCreate() or your main Activity
NetworkMetricsSdk.init(
    context = this,
    config = NetworkMetricsConfig(
        backendUrl = "https://your-backend.example.com/metrics",
        udpHost    = "your-udp-server.example.com",
        udpPort    = 5005,
        tcpPort    = 8230,
        intervalMs = 30 * 60 * 1000L, // measure every 30 minutes
        notificationTitle = "Network Monitor",
        notificationText  = "Collecting network quality data…",
    )
)

// After location + phone state permissions are granted:
NetworkMetricsSdk.start(context)
```

### 2. Stop

```kotlin
NetworkMetricsSdk.stop(context)
```

### 3. Request permissions (example)

```kotlin
val permissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
)
ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE)
```

---

## Configuration Reference

```kotlin
data class NetworkMetricsConfig(
    // Required
    val backendUrl: String,               // HTTPS POST endpoint

    // Packet loss server (optional — skipped if blank)
    val udpHost: String = "",             // UDP/TCP echo server hostname or IP
    val udpPort: Int = 5005,              // UDP port
    val tcpPort: Int = 8230,              // TCP fallback port

    // Scheduling
    val intervalMs: Long = 30 * 60_000L, // Measurement cycle interval (ms)

    // Feature flags
    val enableSpeed: Boolean = true,
    val enablePacketLoss: Boolean = true,
    val enableStreaming: Boolean = true,
    val enableSocialLatency: Boolean = true,

    // Notification (required by Android for ForegroundService)
    val notificationTitle: String = "Network Metrics",
    val notificationText: String = "Collecting network quality data…",
    val notificationIconRes: Int = android.R.drawable.stat_sys_download,

    // Security
    val authHeader: String? = null,       // e.g. "Bearer <token>"
)
```

---

## Payload Format

Each measurement cycle produces one JSON record posted to `backendUrl`:

```json
{
  "testId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-04-18T10:30:00Z",
  "sdkVersion": "1.0.0",

  "speed": {
    "downloadMbps": 87.4,
    "uploadMbps": 24.1,
    "latencyMs": 18.5,
    "jitterMs": 3.2,
    "loadedLatencyMs": 42.0,
    "serverName": "Cloudflare",
    "serverLocation": "CDG"
  },

  "udpPacketLoss": {
    "sent": 50,
    "received": 49,
    "lossPercent": 2.0,
    "method": "udp"
  },

  "streaming": {
    "startTimeMs": 320,
    "rebufferCount": 0,
    "rebufferDurationMs": 0,
    "avgBitrateKbps": 2400,
    "durationMeasuredMs": 15000,
    "bytesDownloaded": 4500000,
    "error": null
  },

  "socialLatency": [
    { "service": "WhatsApp",  "ttfbMs": 182, "reachable": true },
    { "service": "Instagram", "ttfbMs": 210, "reachable": true },
    { "service": "YouTube",   "ttfbMs": 195, "reachable": true },
    { "service": "TikTok",    "ttfbMs": 340, "reachable": true },
    { "service": "X",         "ttfbMs": 220, "reachable": true },
    { "service": "Facebook",  "ttfbMs": 198, "reachable": true }
  ],

  "radio": {
    "rsrp": -85,
    "rsrq": -12,
    "sinr": 14,
    "rssi": null,
    "cqi": 10,
    "ci": 12345678,
    "pci": 42,
    "tac": 5020,
    "earfcn": 1300,
    "isNrAvailable": true,
    "networkGeneration": "5G",
    "signalStrengthLevel": "GOOD",
    "technology": "cellular"
  },

  "network": {
    "connectionType": "cellular",
    "ip": "82.64.114.218",
    "asn": "AS3215",
    "isp": "Orange S.A.",
    "city": "Paris",
    "country": "France",
    "countryCode": "FR",
    "cfColo": "CDG",
    "cfServerCity": "Paris",
    "isLocallyServed": true,
    "ipVersion": "IPv4"
  },

  "geo": {
    "lat": 48.8566,
    "lon": 2.3522,
    "accuracy": 4.5,
    "altitude": 35.0,
    "speed": 0.0,
    "bearing": null,
    "provider": "fused"
  },

  "device": {
    "manufacturer": "Google",
    "model": "Pixel 8",
    "osVersion": "14",
    "sdkInt": 34,
    "simOperatorName": "Orange F",
    "mcc": "208",
    "mnc": "01",
    "batteryLevel": 78,
    "isCharging": false
  },

  "scores": {
    "streaming": { "score": 92, "label": "Excellent" },
    "gaming":    { "score": 85, "label": "Excellent" },
    "rtc":       { "score": 88, "label": "Excellent" }
  },

  "mos": 4.2
}
```

---

## Architecture

```
NetworkMetricsSdk.start()
        │
        ▼
NetworkMetricsService  ← ForegroundService / START_STICKY
        │
        ├── FusedLocationProvider  ← GPS passive continuous
        │
        └── Measurement cycle (configurable interval)
                │
                ├── SpeedMeasurement         → Cloudflare multi-thread HTTP
                ├── PacketLossMeasurement    → UDP native / TCP fallback
                ├── StreamingMeasurement     → HLS segment download
                ├── SocialLatencyMeasurement → HEAD probes × 6 services
                ├── RadioMeasurement         → TelephonyManager getAllCellInfo()
                ├── NetworkContextMeasurement→ CF trace + ipapi.co
                ├── DeviceMeasurement        → Build + TelephonyManager
                ├── MosCalculator            → ITU-T G.107
                └── QualityScoresCalculator  → Streaming / Gaming / RTC
                        │
                        ▼
               NetworkMetricsRecord (JSON)
                        │
                        ▼
               POST → backendUrl
```

---

## Radio Metrics — Permission Notes

| API | Permission required | Android version |
|-----|-------------------|----------------|
| `getAllCellInfo()` — RSRP, RSRQ, SINR, Cell ID | `ACCESS_FINE_LOCATION` + `READ_PHONE_STATE` | Android 9+ |
| `getDataNetworkType()` — 4G/5G | `READ_PHONE_STATE` | Android 7+ |
| `TelephonyDisplayInfo` — 5G NSA | `READ_PHONE_STATE` | Android 11+ |

> `READ_BASIC_PHONE_STATE` (Android 13+) does **not** grant access to cell info APIs. `READ_PHONE_STATE` is always required.

---

## Cell Identity Fields

| Field | Technology | Description |
|-------|-----------|-------------|
| `ci` | LTE (CI), NR (NCI), WCDMA (CID) | Unique cell identifier — maps to a physical antenna |
| `pci` | LTE / NR | Physical Cell ID (0–503) — layer-1 cell discrimination |
| `tac` | LTE / NR | Tracking Area Code — cell grouping for paging |
| `earfcn` | LTE (EARFCN) / NR (NR-ARFCN) / WCDMA (UARFCN) | Radio frequency channel |

These fields enable **antenna-level network cartography** — associating every measurement record with a specific physical cell tower. Invaluable for regulatory reporting and radio planning.

---

## Quality Scores

Three composite scores are computed algorithmically from raw metrics:

| Score | Algorithm inputs | Use case |
|-------|-----------------|---------|
| `streaming` | Download speed + latency | Video streaming suitability |
| `gaming` | Latency + jitter + packet loss | Real-time gaming suitability |
| `rtc` | Latency + jitter + packet loss | VoIP / video call suitability |

Each score is 0–100 with label: **Poor** (< 50) / **Core** (50–79) / **Excellent** (≥ 80).

---

## UDP Packet Loss — Fallback Strategy

```
Probe UDP (1 packet, 2s timeout)
    ├── SUCCESS → Full UDP measurement (50 packets, port 5005)
    └── FAIL    → TCP echo measurement (50 packets, port 8230)
                  result.method = "tcp"
```

Carriers using CGNAT (e.g. Orange France mobile) commonly block UDP on non-standard outbound ports. The automatic fallback ensures packet loss is always measured regardless of carrier policy.

---

## Data Privacy

- No personal data collected (no name, email, phone number, MSISDN)
- Device UUID is pseudonymous — cannot identify an individual
- Location data used exclusively for geographic network analysis
- All transmissions over HTTPS
- No data collected if location permission is denied

---

## Changelog

### 1.0.0 (April 2026)
- Initial release
- ForegroundService with START_STICKY for H24 operation
- Speed test via Cloudflare multi-threaded HTTP
- UDP packet loss with automatic TCP fallback
- HLS streaming download measurement
- Social media latency for 6 platforms
- Full radio signal: RSRP, RSRQ, SINR, CQI, Cell ID, PCI, TAC, EARFCN
- 5G NSA detection via TelephonyDisplayInfo
- GPS passive via FusedLocationProvider
- Network context via Cloudflare trace + ipapi.co
- Quality scores: Streaming / Gaming / RTC
- MOS (ITU-T G.107)
- Configurable measurement interval, feature flags, auth header

---

## License

```
MIT License

Copyright (c) 2026 Kevin Dupas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
