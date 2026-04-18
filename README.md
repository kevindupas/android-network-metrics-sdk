# Network Metrics SDK

[![Release](https://img.shields.io/github/v/release/kevindupas/android-network-metrics-sdk)](https://github.com/kevindupas/android-network-metrics-sdk/releases)
[![JitPack](https://jitpack.io/v/kevindupas/android-network-metrics-sdk.svg)](https://jitpack.io/#kevindupas/android-network-metrics-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/Android-API%2026%2B-green?logo=android)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)](https://kotlinlang.org)

Android SDK for **continuous, passive mobile network quality measurement**. Runs invisibly via WorkManager — no persistent notification, no battery optimisation bypass required.

## What it measures

| Category | Metrics |
|----------|---------|
| **Speed** | Download (Mbps), Upload (Mbps), Latency (ms), Jitter (ms), Loaded Latency (ms) |
| **Packet Loss** | UDP loss % — automatic TCP fallback if carrier blocks UDP |
| **Video Streaming** | HLS startup time, rebuffer count, rebuffer duration, avg bitrate |
| **Social Media Latency** | TTFB to WhatsApp, Instagram, YouTube, TikTok, X, Facebook |
| **Radio Signal** | RSRP, RSRQ, SINR, RSSI, CQI, CI, PCI, TAC, LAC, EARFCN, bandwidth, PSC |
| **Radio Context** | Network generation (2G→5G), roaming, 5G mode (NSA vs SA) |
| **Neighboring Cells** | All visible cells (LTE/NR/WCDMA/GSM) — registered + non-registered |
| **DNS** | Resolution time, resolved IPs, success flag |
| **Web Browsing** | Per-target: DNS time, TCP connect time, TLS handshake time, TTFB, HTTP status |
| **Network Context** | ISP, ASN, IP address, Cloudflare PoP, IP version (IPv4/IPv6/dual) |
| **Location** | GPS lat/lon, accuracy, altitude, speed, bearing (FusedLocationProvider) |
| **Device** | Manufacturer, model, OS version, SIM operator, MCC/MNC, battery, RAM, CPU load, thermal state |
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

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.kevindupas:android-network-metrics-sdk:v1.0.10")
}
```

### AAR (manual)

Download `library-release.aar` from the [latest release](https://github.com/kevindupas/android-network-metrics-sdk/releases/latest):

```kotlin
dependencies {
    implementation(files("libs/network-metrics-sdk.aar"))
    // Required transitive dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
```

---

## Android Manifest

The SDK declares all required permissions via manifest merging. If you need to declare them explicitly:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_BASIC_PHONE_STATE" />
```

> **No `FOREGROUND_SERVICE` permission required.** The SDK uses WorkManager, which runs silently in the background without a persistent notification.

> **Why `READ_PHONE_STATE`?** Required to access `TelephonyDisplayInfo` (5G NSA/SA detection), `getDataNetworkType()`, and `getAllCellInfo()` (Cell ID, RSRP, RSRQ, SINR). `READ_BASIC_PHONE_STATE` alone is insufficient for these APIs on all Android versions.

---

## Quick Start (Kotlin)

```kotlin
// In Application.onCreate() or after permissions are granted
NetworkMetricsSdk.init(
    context = this,
    config = NetworkMetricsConfig(
        backendUrl = "https://your-backend.example.com/metrics",
        udpHost    = "your-udp-server.example.com",
        intervalMs = 30 * 60_000L, // every 30 minutes
    )
)

// Start periodic background measurement
NetworkMetricsSdk.start(context)

// Trigger an immediate one-shot measurement
NetworkMetricsSdk.measureNow(context)

// Read the last result (stored locally in SharedPreferences)
val json: String? = NetworkMetricsSdk.getLastResult(context)
val ts: Long = NetworkMetricsSdk.getLastResultTimestamp(context)

// Stop periodic measurement
NetworkMetricsSdk.stop(context)
```

## Quick Start (Java — Builder pattern)

```java
NetworkMetricsConfig config = NetworkMetricsConfig.builder("https://your-backend.example.com/metrics")
    .udpHost("your-udp-server.example.com")
    .intervalMs(30 * 60 * 1000L)
    .enableStreaming(false)          // disable on 3G prepaid
    .speedDownloadDurationMs(3000L)  // 3G preset
    .speedUploadDurationMs(2000L)
    .speedThreadCount(2)
    .authHeader("Bearer " + token)
    .build();

NetworkMetricsSdk.INSTANCE.init(context, config);
NetworkMetricsSdk.INSTANCE.start(context);
```

---

## Configuration Reference

```kotlin
NetworkMetricsConfig(
    // ── Required ──────────────────────────────────────────────────────────
    backendUrl: String,                   // HTTPS POST endpoint

    // ── Packet loss server (optional — skipped if blank) ──────────────────
    udpHost: String = "",                 // UDP/TCP echo server hostname or IP
    udpPort: Int = 5005,
    tcpPort: Int = 8230,

    // ── Scheduling ────────────────────────────────────────────────────────
    intervalMs: Long = 30 * 60_000L,     // Minimum: 900_000 (15 min, WorkManager limit)

    // ── Feature flags ─────────────────────────────────────────────────────
    enableSpeed: Boolean = true,
    enablePacketLoss: Boolean = true,
    enableStreaming: Boolean = true,      // ~3 MB/cycle — disable on 3G prepaid
    enableSocialLatency: Boolean = true,
    enableDns: Boolean = true,
    enableWebBrowsing: Boolean = true,
    enableNeighboringCells: Boolean = true,

    // ── Speed test tuning ─────────────────────────────────────────────────
    speedDownloadDurationMs: Long = 8_000L,   // Duration-based — accurate on 3G→5G
    speedUploadDurationMs: Long = 6_000L,
    speedThreadCount: Int = 3,                // Parallel download streams

    // ── Web browsing targets ───────────────────────────────────────────────
    // Default: Google, WhatsApp, YouTube, Facebook
    // Override per deployment or via remoteConfigUrl
    webTargets: List<WebTarget> = NetworkMetricsConfig.DEFAULT_WEB_TARGETS,

    // ── Remote config (optional) ──────────────────────────────────────────
    // SDK fetches { "targets": [{ "name": "...", "url": "..." }] } from this URL
    // Cached 1 hour. Falls back to webTargets on error.
    remoteConfigUrl: String? = null,

    // ── Security ──────────────────────────────────────────────────────────
    authHeader: String? = null,           // e.g. "Bearer <token>"
)
```

### Network presets for prepaid Africa

```kotlin
// 3G prepaid (~3 MB/cycle, ~24 MB/day at 2h interval)
NetworkMetricsConfig(
    backendUrl = "...",
    intervalMs = 2 * 60 * 60_000L,
    enableStreaming = false,
    speedDownloadDurationMs = 3_000L,
    speedUploadDurationMs = 2_000L,
    speedThreadCount = 2,
)

// 4G balanced (~12 MB/cycle, ~576 MB/day at 30min)
NetworkMetricsConfig(
    backendUrl = "...",
    speedDownloadDurationMs = 5_000L,
    speedUploadDurationMs = 4_000L,
    speedThreadCount = 3,
)

// 5G / WiFi full precision (~30 MB/cycle, ~1.4 GB/day at 30min)
NetworkMetricsConfig(
    backendUrl = "...",
    speedDownloadDurationMs = 8_000L,
    speedUploadDurationMs = 6_000L,
    speedThreadCount = 3,
)
```

---

## On-Demand Measurement

```kotlin
// Trigger an immediate cycle without waiting for the next scheduled run
NetworkMetricsSdk.measureNow(context)

// WorkManager enqueues it with ExistingWorkPolicy.REPLACE
// Result is stored in SharedPreferences once complete

// Poll for result (or use your own observer pattern)
val json = NetworkMetricsSdk.getLastResult(context)        // String? — raw JSON
val ts   = NetworkMetricsSdk.getLastResultTimestamp(context) // Long — epoch ms
```

---

## Remote Config

The SDK can fetch web browsing targets dynamically from your backend:

```json
// GET https://your-backend.example.com/sdk-config
{
  "targets": [
    { "name": "WhatsApp",  "url": "https://web.whatsapp.com/" },
    { "name": "eCitizen",  "url": "https://ecitizen.go.ke/" },
    { "name": "MTN",       "url": "https://mtn.com/" }
  ]
}
```

```kotlin
NetworkMetricsConfig(
    backendUrl = "https://your-backend.example.com/metrics",
    remoteConfigUrl = "https://your-backend.example.com/sdk-config",
)
```

- Config cached **1 hour** in memory
- Falls back to `webTargets` (default or custom) on network error
- Allows per-country target configuration without app rebuild

---

## Architecture

```
NetworkMetricsSdk.start()  /  measureNow()
        │
        ▼
WorkManager (PeriodicWorkRequest / OneTimeWorkRequest)
        │  invisible — no notification — survives battery optimisation
        ▼
NetworkMetricsWorker (CoroutineWorker)
        │
        ├── [parallel]
        │     ├── SpeedMeasurement          → Cloudflare multi-thread HTTP (duration-based)
        │     ├── SocialLatencyMeasurement  → HEAD probes × 6 services
        │     ├── StreamingMeasurement      → HLS segment download
        │     ├── DnsMeasurement            → InetAddress.getAllByName()
        │     └── WebBrowsingMeasurement    → OkHttp EventListener (DNS+TCP+TLS+TTFB)
        │
        ├── [sequential]
        │     ├── PacketLossMeasurement     → UDP / TCP fallback
        │     ├── RadioMeasurement          → TelephonyManager.getAllCellInfo()
        │     ├── NeighboringCellsMeasurement → all visible cells
        │     ├── NetworkContextMeasurement → CF trace + ipapi.co
        │     └── DeviceMeasurement         → Build + TelephonyManager + /proc/stat
        │
        ├── MosCalculator                  → ITU-T G.107
        ├── QualityScoresCalculator        → Streaming / Gaming / RTC
        │
        ├── SharedPreferences              ← persist last result locally
        └── POST → backendUrl
```

---

## Payload Format

Full JSON structure posted to `backendUrl` after each cycle:

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
    "lac": null,
    "earfcn": 1300,
    "bandwidth": 20000,
    "psc": null,
    "isNrAvailable": true,
    "isRoaming": false,
    "nrMode": "NSA",
    "networkGeneration": "5G",
    "signalStrengthLevel": "GOOD",
    "technology": "cellular"
  },

  "neighboringCells": [
    {
      "type": "LTE",
      "pci": 42,
      "ci": 12345678,
      "rsrp": -85,
      "rsrq": -12,
      "rssi": null,
      "tac": 5020,
      "lac": null,
      "earfcn": 1300,
      "isRegistered": true
    },
    {
      "type": "LTE",
      "pci": 107,
      "ci": 12345679,
      "rsrp": -98,
      "rsrq": -15,
      "rssi": null,
      "tac": 5020,
      "lac": null,
      "earfcn": 1300,
      "isRegistered": false
    }
  ],

  "dns": {
    "resolveMs": 24,
    "host": "google.com",
    "resolvedIps": ["142.250.179.46", "2a00:1450:4007:818::200e"],
    "success": true
  },

  "webBrowsing": [
    {
      "name": "Google",
      "url": "https://www.google.com/",
      "dnsMs": 24,
      "tcpMs": 18,
      "tlsMs": 32,
      "ttfbMs": 12,
      "totalMs": 86,
      "httpStatus": 200,
      "success": true,
      "error": null
    },
    {
      "name": "WhatsApp",
      "url": "https://web.whatsapp.com/",
      "dnsMs": 31,
      "tcpMs": 22,
      "tlsMs": 45,
      "ttfbMs": 18,
      "totalMs": 116,
      "httpStatus": 200,
      "success": true,
      "error": null
    }
  ],

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
    "ipVersion": "dual"
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
    "isCharging": false,
    "ramUsedMb": 312,
    "cpuLoadPercent": 18.4,
    "thermalStatus": "NONE"
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

## Field Reference

### `radio`

| Field | Type | Technology | Description |
|-------|------|-----------|-------------|
| `rsrp` | `Int?` | LTE / NR | Reference Signal Received Power (dBm). LTE: −140→−44, good > −80 |
| `rsrq` | `Int?` | LTE / NR | Reference Signal Received Quality (dB). Good > −10 |
| `sinr` | `Int?` | LTE / NR | Signal-to-Interference+Noise Ratio (dB). Good > 10 |
| `rssi` | `Int?` | GSM / WCDMA | Received Signal Strength Indicator (dBm) |
| `cqi` | `Int?` | LTE | Channel Quality Indicator (0–15). Higher = better |
| `ci` | `Long?` | LTE (CI), NR (NCI), WCDMA (CID) | Unique cell identifier |
| `pci` | `Int?` | LTE / NR | Physical Cell ID (0–503) |
| `tac` | `Int?` | LTE / NR | Tracking Area Code |
| `lac` | `Int?` | GSM / WCDMA | Location Area Code |
| `earfcn` | `Int?` | LTE (EARFCN) / NR (NR-ARFCN) / WCDMA (UARFCN) | Radio frequency channel |
| `bandwidth` | `Int?` | LTE | Channel bandwidth in kHz (e.g. 20000 = 20 MHz) |
| `psc` | `Int?` | WCDMA | Primary Scrambling Code |
| `isNrAvailable` | `Boolean` | — | 5G NR signal detected |
| `isRoaming` | `Boolean` | — | Device on a visited network |
| `nrMode` | `String?` | NR | `"NSA"` (Non-Standalone, 4G anchor) / `"SA"` (Standalone, true 5G) / `null` |
| `networkGeneration` | `String` | — | `"2G"` / `"3G"` / `"4G"` / `"5G"` / `"WiFi"` / `"Unknown"` |
| `signalStrengthLevel` | `String` | — | `"NONE"` / `"POOR"` / `"MODERATE"` / `"GOOD"` / `"GREAT"` |
| `technology` | `String` | — | `"cellular"` / `"WiFi"` / `"none"` |

### `neighboringCells[]`

Each element represents a visible cell (registered or not):

| Field | Type | Description |
|-------|------|-------------|
| `type` | `String` | `"LTE"` / `"NR"` / `"WCDMA"` / `"GSM"` |
| `pci` | `Int?` | Physical Cell ID |
| `ci` | `Long?` | Cell identifier |
| `rsrp` | `Int?` | Signal strength (dBm) |
| `rsrq` | `Int?` | Signal quality (dB) |
| `rssi` | `Int?` | GSM/WCDMA signal (dBm) |
| `tac` | `Int?` | Tracking Area Code |
| `lac` | `Int?` | Location Area Code |
| `earfcn` | `Int?` | Frequency channel |
| `isRegistered` | `Boolean` | `true` = serving cell, `false` = neighbor |

### `dns`

| Field | Type | Description |
|-------|------|-------------|
| `resolveMs` | `Long` | Full DNS resolution time (ms) |
| `host` | `String` | Queried hostname (default: `google.com`) |
| `resolvedIps` | `List<String>` | Returned IP addresses (IPv4 + IPv6 if dual-stack) |
| `success` | `Boolean` | `false` = DNS failure / timeout |

### `webBrowsing[]`

Per-target HTTP timing breakdown via OkHttp EventListener:

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Target name (e.g. `"WhatsApp"`) |
| `url` | `String` | Target URL |
| `dnsMs` | `Long?` | DNS resolution time |
| `tcpMs` | `Long?` | TCP handshake time (excluding TLS) |
| `tlsMs` | `Long?` | TLS handshake time (`null` for HTTP) |
| `ttfbMs` | `Long?` | Time to first byte (server response time) |
| `totalMs` | `Long?` | Total end-to-end time |
| `httpStatus` | `Int?` | HTTP response code |
| `success` | `Boolean` | `true` if 2xx or 3xx |
| `error` | `String?` | Exception message on failure |

### `device`

| Field | Type | Description |
|-------|------|-------------|
| `manufacturer` | `String` | e.g. `"Google"`, `"Samsung"` |
| `model` | `String` | Device model |
| `osVersion` | `String` | Android version string (e.g. `"14"`) |
| `sdkInt` | `Int` | Android API level |
| `simOperatorName` | `String?` | SIM operator name |
| `mcc` | `String?` | Mobile Country Code |
| `mnc` | `String?` | Mobile Network Code |
| `batteryLevel` | `Int?` | Battery percentage (0–100) |
| `isCharging` | `Boolean?` | Charging state |
| `ramUsedMb` | `Int?` | App RAM usage from `Debug.MemoryInfo.totalPss` |
| `cpuLoadPercent` | `Double?` | System CPU load — 200ms `/proc/stat` sample |
| `thermalStatus` | `String?` | `"NONE"` / `"LIGHT"` / `"MODERATE"` / `"SEVERE"` / `"CRITICAL"` / `"EMERGENCY"` / `"SHUTDOWN"` (API 29+) |

---

## Radio Metrics — Permission Notes

| API | Permission required | Android version |
|-----|-------------------|----------------|
| `getAllCellInfo()` — RSRP, RSRQ, SINR, Cell ID | `ACCESS_FINE_LOCATION` + `READ_PHONE_STATE` | Android 9+ |
| `getDataNetworkType()` — 4G/5G | `READ_PHONE_STATE` | Android 7+ |
| `TelephonyDisplayInfo` — 5G NSA/SA | `READ_PHONE_STATE` | Android 11+ |
| `isNetworkRoaming()` — roaming | none | All |
| `PowerManager.currentThermalStatus` — thermal | none | Android 10+ |

---

## Quality Scores

| Score | Inputs | Use case |
|-------|--------|---------|
| `streaming` | Download speed + latency | Video streaming suitability |
| `gaming` | Latency + jitter + packet loss | Real-time gaming suitability |
| `rtc` | Latency + jitter + packet loss | VoIP / video call suitability |

Range 0–100. Labels: **Poor** (< 50) / **Core** (50–79) / **Excellent** (≥ 80).

---

## UDP Packet Loss — Fallback Strategy

```
Probe UDP (1 packet, 2s timeout)
    ├── SUCCESS → Full UDP measurement (50 packets, port 5005)
    └── FAIL    → TCP echo measurement (50 packets, port 8230)
                  result.method = "tcp"
```

Carriers using CGNAT (common on African mobile networks) block UDP on non-standard ports. The TCP fallback ensures packet loss is always measured.

---

## Data Privacy

- No personal data collected (no name, email, phone number, MSISDN)
- Device UUID is pseudonymous — cannot identify an individual
- Location used exclusively for geographic network analysis
- All transmissions over HTTPS
- No data collected if location permission is denied

---

## Changelog

### v1.0.10 (April 2026)
- `measureNow()` — on-demand one-shot measurement via WorkManager
- `getLastResult()` / `getLastResultTimestamp()` — read last result from SharedPreferences
- Worker persists every cycle result locally
- Java-friendly `NetworkMetricsConfig.Builder` pattern

### v1.0.9 (April 2026)
- DNS measurement — `resolveMs`, `resolvedIps`, `success`
- Roaming detection — `radio.isRoaming`
- 5G NSA vs SA distinction — `radio.nrMode` (`"NSA"` / `"SA"` / `null`)
- Neighboring cells — `neighboringCells[]` all visible LTE/NR/WCDMA/GSM cells
- Thermal state — `device.thermalStatus` (API 29+)
- Web browsing — `webBrowsing[]` with DNS+TCP+TLS+TTFB per target
- Remote config — `remoteConfigUrl` for per-country target configuration
- New feature flags: `enableDns`, `enableWebBrowsing`, `enableNeighboringCells`

### v1.0.8 (April 2026)
- MedUX parity fields: `radio.lac`, `radio.bandwidth`, `radio.psc`
- GSM branch (2G) in RadioMeasurement
- `device.ramUsedMb` — `Debug.MemoryInfo.totalPss`
- `device.cpuLoadPercent` — `/proc/stat` 200ms delta

### v1.0.7 (April 2026)
- WorkManager replaces ForegroundService — invisible, no persistent notification
- Duration-based speed test — accurate on 3G→5G
- Speed test tuning: `speedDownloadDurationMs`, `speedUploadDurationMs`, `speedThreadCount`

### v1.0.0 (April 2026)
- Initial release
- Speed test via Cloudflare multi-threaded HTTP
- UDP packet loss with TCP fallback
- HLS streaming measurement
- Social media latency — 6 platforms
- Full radio: RSRP, RSRQ, SINR, CQI, CI, PCI, TAC, EARFCN
- 5G NSA detection
- GPS via FusedLocationProvider
- Network context via Cloudflare trace + ipapi.co
- Quality scores: Streaming / Gaming / RTC
- MOS (ITU-T G.107)

---

## License

MIT License — Copyright (c) 2026 Kevin Dupas

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
