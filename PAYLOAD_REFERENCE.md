# Network Metrics SDK — Payload Reference

**Version:** 1.0.10 · **Format:** JSON · **Transport:** HTTPS POST

This document describes every field your backend receives after each measurement cycle. All fields are present in every payload — optional fields are `null` when unavailable (permissions denied, technology not supported, measurement disabled).

---

## Top-Level Structure

```json
{
  "testId":    "uuid-v4",
  "timestamp": "2026-04-18T10:30:00Z",
  "sdkVersion": "1.0.0",

  "speed":          { ... },
  "udpPacketLoss":  { ... },
  "streaming":      { ... },
  "socialLatency":  [ ... ],
  "radio":          { ... },
  "neighboringCells": [ ... ],
  "dns":            { ... },
  "webBrowsing":    [ ... ],
  "network":        { ... },
  "geo":            { ... },
  "device":         { ... },
  "scores":         { ... },
  "mos":            4.2
}
```

| Field | Type | Always present | Description |
|-------|------|:-:|-------------|
| `testId` | `string` | ✓ | UUID v4 — unique per measurement cycle |
| `timestamp` | `string` | ✓ | ISO 8601 UTC |
| `sdkVersion` | `string` | ✓ | SDK version string |
| `speed` | `object\|null` | | `null` if `enableSpeed=false` |
| `udpPacketLoss` | `object\|null` | | `null` if `enablePacketLoss=false` or no UDP host configured |
| `streaming` | `object\|null` | | `null` if `enableStreaming=false` |
| `socialLatency` | `array` | ✓ | Empty array if `enableSocialLatency=false` |
| `radio` | `object\|null` | | `null` on API < 29 or missing permissions |
| `neighboringCells` | `array` | ✓ | Empty if `enableNeighboringCells=false` or API < 29 |
| `dns` | `object\|null` | | `null` if `enableDns=false` |
| `webBrowsing` | `array` | ✓ | Empty if `enableWebBrowsing=false` |
| `network` | `object` | ✓ | Always present (may have `null` sub-fields) |
| `geo` | `object\|null` | | `null` if location permission denied or no fix |
| `device` | `object` | ✓ | Always present |
| `scores` | `object\|null` | | `null` if speed test disabled |
| `mos` | `number\|null` | | `null` if speed test disabled |

---

## `speed`

Speed test via Cloudflare multi-threaded HTTP. Duration-based measurement (not fixed-chunk) — accurate across 3G→5G.

```json
"speed": {
  "downloadMbps": 87.4,
  "uploadMbps":   24.1,
  "latencyMs":    18.5,
  "jitterMs":     3.2,
  "loadedLatencyMs": 42.0,
  "serverName":     "Cloudflare",
  "serverLocation": "CDG"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `downloadMbps` | `number` | Download throughput |
| `uploadMbps` | `number` | Upload throughput |
| `latencyMs` | `number` | Unloaded RTT (average of 8 pings) |
| `jitterMs` | `number` | Latency variation |
| `loadedLatencyMs` | `number\|null` | RTT measured during download load |
| `serverName` | `string\|null` | Speed test server name |
| `serverLocation` | `string\|null` | IATA code of Cloudflare PoP used |

---

## `udpPacketLoss`

UDP echo test against a configurable server. Automatic TCP fallback if UDP is blocked (common with CGNAT on African mobile networks).

```json
"udpPacketLoss": {
  "sent":        50,
  "received":    48,
  "lossPercent": 4.0,
  "method":      "udp"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `sent` | `number` | Packets sent |
| `received` | `number` | Packets received |
| `lossPercent` | `number` | Loss percentage (0.0–100.0) |
| `method` | `string` | `"udp"` or `"tcp"` (fallback) |

---

## `streaming`

HLS video segment download measurement (Big Buck Bunny). Tests video startup and rebuffering under current network conditions.

```json
"streaming": {
  "startTimeMs":        320,
  "rebufferCount":      0,
  "rebufferDurationMs": 0,
  "avgBitrateKbps":     2400,
  "durationMeasuredMs": 15000,
  "bytesDownloaded":    4500000,
  "error":              null
}
```

| Field | Type | Description |
|-------|------|-------------|
| `startTimeMs` | `number\|null` | Time to first frame (ms) |
| `rebufferCount` | `number` | Number of buffering interruptions |
| `rebufferDurationMs` | `number` | Total rebuffering time (ms) |
| `avgBitrateKbps` | `number\|null` | Average HLS bitrate selected |
| `durationMeasuredMs` | `number` | Total measurement window (ms) |
| `bytesDownloaded` | `number` | Total bytes downloaded |
| `error` | `string\|null` | Error message if measurement failed |

---

## `socialLatency`

TTFB (Time To First Byte) to major social platforms. Measures how fast key services are reachable from the device.

```json
"socialLatency": [
  { "service": "WhatsApp",  "ttfbMs": 182, "reachable": true },
  { "service": "Instagram", "ttfbMs": 210, "reachable": true },
  { "service": "YouTube",   "ttfbMs": 195, "reachable": true },
  { "service": "TikTok",    "ttfbMs": 340, "reachable": true },
  { "service": "X",         "ttfbMs": 220, "reachable": true },
  { "service": "Facebook",  "ttfbMs": 198, "reachable": true }
]
```

| Field | Type | Description |
|-------|------|-------------|
| `service` | `string` | Platform name |
| `ttfbMs` | `number\|null` | TTFB in ms, `null` if unreachable |
| `reachable` | `boolean` | Whether the service responded |

---

## `radio`

Radio signal data from the serving cell. Fields are `null` if the technology doesn't support them (e.g. `rsrp` is null for GSM).

```json
"radio": {
  "rsrp": -85,
  "rsrq": -12,
  "sinr": 14,
  "rssi": null,
  "cqi":  10,
  "ci":   12345678,
  "pci":  42,
  "tac":  5020,
  "lac":  null,
  "earfcn":    1300,
  "bandwidth": 20000,
  "psc":       null,
  "isNrAvailable":     true,
  "isRoaming":         false,
  "nrMode":            "NSA",
  "networkGeneration": "5G",
  "signalStrengthLevel": "GOOD",
  "technology":          "cellular"
}
```

| Field | Type | Technologies | Description |
|-------|------|-------------|-------------|
| `rsrp` | `number\|null` | LTE, NR | Reference Signal Received Power (dBm) · Good: > −80 |
| `rsrq` | `number\|null` | LTE, NR | Reference Signal Received Quality (dB) · Good: > −10 |
| `sinr` | `number\|null` | LTE, NR | Signal-to-Interference+Noise Ratio (dB) · Good: > 10 |
| `rssi` | `number\|null` | GSM, WCDMA | Received Signal Strength (dBm) |
| `cqi` | `number\|null` | LTE | Channel Quality Indicator (0–15) · Higher = better |
| `ci` | `number\|null` | LTE (CI), NR (NCI), WCDMA (CID), GSM (CID) | Cell identifier — maps to one physical antenna |
| `pci` | `number\|null` | LTE, NR | Physical Cell ID (0–503) |
| `tac` | `number\|null` | LTE, NR | Tracking Area Code |
| `lac` | `number\|null` | GSM, WCDMA | Location Area Code |
| `earfcn` | `number\|null` | LTE (EARFCN), NR (NR-ARFCN), WCDMA (UARFCN) | Radio frequency channel |
| `bandwidth` | `number\|null` | LTE | Channel bandwidth in kHz (e.g. 20000 = 20 MHz) |
| `psc` | `number\|null` | WCDMA | Primary Scrambling Code |
| `isNrAvailable` | `boolean` | — | 5G NR signal currently detected |
| `isRoaming` | `boolean` | — | Device registered on a visited (non-home) network |
| `nrMode` | `string\|null` | NR | `"NSA"` = Non-Standalone (LTE anchor + NR data) · `"SA"` = Standalone (true 5G core) · `null` = not on 5G |
| `networkGeneration` | `string` | — | `"2G"` / `"3G"` / `"4G"` / `"5G"` / `"WiFi"` / `"Unknown"` |
| `signalStrengthLevel` | `string` | — | `"NONE"` / `"POOR"` / `"MODERATE"` / `"GOOD"` / `"GREAT"` |
| `technology` | `string` | — | `"cellular"` / `"WiFi"` / `"none"` |

### Signal threshold reference

| Metric | Excellent | Good | Fair | Poor |
|--------|-----------|------|------|------|
| RSRP (dBm) | > −80 | −80→−90 | −90→−100 | < −100 |
| RSRQ (dB) | > −10 | −10→−15 | −15→−20 | < −20 |
| SINR (dB) | > 20 | 13→20 | 0→13 | < 0 |

### NSA vs SA distinction

| `nrMode` | Meaning | Regulatory relevance |
|----------|---------|---------------------|
| `null` | No 5G signal | Device on LTE or below |
| `"NSA"` | 5G NR data, LTE control plane | Early 5G deployment — relies on 4G infrastructure |
| `"SA"` | True 5G standalone core | Full 5G deployment — independent of LTE |

This distinction is critical for regulators evaluating 5G deployment maturity. NSA is the dominant mode in early rollouts; SA indicates a fully independent 5G network.

---

## `neighboringCells`

All visible cells detected by the device — including non-registered neighbors. Enables network density mapping and handover analysis.

```json
"neighboringCells": [
  {
    "type": "LTE",
    "pci": 42, "ci": 12345678,
    "rsrp": -85, "rsrq": -12, "rssi": null,
    "tac": 5020, "lac": null, "earfcn": 1300,
    "isRegistered": true
  },
  {
    "type": "LTE",
    "pci": 107, "ci": 12345679,
    "rsrp": -98, "rsrq": -15, "rssi": null,
    "tac": 5020, "lac": null, "earfcn": 1300,
    "isRegistered": false
  },
  {
    "type": "GSM",
    "pci": null, "ci": 7890,
    "rsrp": null, "rsrq": null, "rssi": -75,
    "tac": null, "lac": 1234, "earfcn": 512,
    "isRegistered": false
  }
]
```

| Field | Description |
|-------|-------------|
| `type` | `"LTE"` / `"NR"` / `"WCDMA"` / `"GSM"` |
| `isRegistered` | `true` = this is the serving cell, `false` = neighbor |

All other fields follow the same definitions as `radio` above.

**Use cases:**
- Count of visible cells → network density indicator
- Non-registered cells → handover candidates, dead zone detection
- Multi-operator cells visible → market structure analysis

---

## `dns`

DNS resolution time for a reference hostname. Reveals DNS infrastructure quality of the operator.

```json
"dns": {
  "resolveMs":    24,
  "host":         "google.com",
  "resolvedIps":  ["142.250.179.46", "2a00:1450:4007:818::200e"],
  "success":      true
}
```

| Field | Description |
|-------|-------------|
| `resolveMs` | Full resolution time (ms) — slow values (> 200ms) indicate DNS infrastructure problems |
| `host` | Queried hostname |
| `resolvedIps` | Returned addresses — both IPv4 and IPv6 if dual-stack |
| `success` | `false` = DNS failure or timeout |

**Regulatory insight:** High `resolveMs` (> 500ms) is a common complaint on African operators and directly impacts all internet applications. DNS issues are often invisible to users but measurable here.

---

## `webBrowsing`

Per-target HTTP timing breakdown. Each phase is measured independently via OkHttp EventListener. Targets are configurable per deployment via `remoteConfigUrl`.

```json
"webBrowsing": [
  {
    "name":        "Google",
    "url":         "https://www.google.com/",
    "dnsMs":       24,
    "tcpMs":       18,
    "tlsMs":       32,
    "ttfbMs":      12,
    "totalMs":     86,
    "httpStatus":  200,
    "success":     true,
    "error":       null
  }
]
```

| Field | Description |
|-------|-------------|
| `dnsMs` | DNS resolution (ms) — service-specific, may differ from `dns.resolveMs` due to caching |
| `tcpMs` | TCP handshake (ms) — pure network round-trip to server |
| `tlsMs` | TLS handshake (ms) — `null` for plain HTTP |
| `ttfbMs` | Time to first byte (ms) — server processing + network |
| `totalMs` | Wall-clock total |
| `httpStatus` | HTTP response code (200, 301, 403…) |
| `success` | `true` if 2xx or 3xx |
| `error` | Exception message on failure (max 120 chars) |

**Diagnostic value:** Comparing `dnsMs` vs `tcpMs` vs `tlsMs` vs `ttfbMs` pinpoints *where* latency occurs — at the DNS layer, in routing, in TLS negotiation, or at the application server.

### Remote config for targets

Send targets from your backend — no app rebuild required:

```
GET https://your-backend.example.com/sdk-config
Content-Type: application/json

{
  "targets": [
    { "name": "WhatsApp",  "url": "https://web.whatsapp.com/" },
    { "name": "eCitizen",  "url": "https://ecitizen.go.ke/" },
    { "name": "LocalGov",  "url": "https://service.example.cm/" }
  ]
}
```

Targets are cached 1 hour on-device. Useful for A/B testing target lists or configuring country-specific services for 16+ deployments without a new APK.

---

## `network`

Network context derived from Cloudflare trace (`1.1.1.1/cdn-cgi/trace`) and enriched with ASN/ISP lookup.

```json
"network": {
  "connectionType": "cellular",
  "ip":            "82.64.114.218",
  "asn":           "AS3215",
  "isp":           "Orange S.A.",
  "city":          "Paris",
  "country":       "France",
  "countryCode":   "FR",
  "cfColo":        "CDG",
  "cfServerCity":  "Paris",
  "isLocallyServed": true,
  "ipVersion":     "dual"
}
```

| Field | Description |
|-------|-------------|
| `connectionType` | `"cellular"` / `"WiFi"` / `"none"` |
| `ip` | Public IP address of the device |
| `asn` | Autonomous System Number (e.g. `"AS3215"`) |
| `isp` | ISP name |
| `city` | City resolved from IP |
| `country` | Country name |
| `countryCode` | ISO 3166-1 alpha-2 (e.g. `"KE"`, `"CM"`, `"CI"`) |
| `cfColo` | Nearest Cloudflare PoP IATA code |
| `cfServerCity` | City name of Cloudflare PoP |
| `isLocallyServed` | `true` if Cloudflare PoP is in the same country as device — indicates local CDN presence |
| `ipVersion` | `"IPv4"` / `"IPv6"` / `"dual"` / `"unknown"` |

**`isLocallyServed`** is a key regulatory metric — it indicates whether the device's traffic is routed to an in-country CDN node or must traverse international links.

---

## `geo`

GPS location from `FusedLocationProvider.lastLocation`. One-shot, no continuous tracking.

```json
"geo": {
  "lat":      48.8566,
  "lon":      2.3522,
  "accuracy": 4.5,
  "altitude": 35.0,
  "speed":    0.0,
  "bearing":  null,
  "provider": "fused"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `lat` | `number` | Latitude (decimal degrees) |
| `lon` | `number` | Longitude (decimal degrees) |
| `accuracy` | `number` | Horizontal accuracy radius (metres) |
| `altitude` | `number\|null` | Altitude (metres) — `null` if unavailable |
| `speed` | `number\|null` | Device speed (m/s) |
| `bearing` | `number\|null` | Bearing (degrees) |
| `provider` | `string\|null` | Location provider |

---

## `device`

Device hardware and system state at time of measurement.

```json
"device": {
  "manufacturer":   "Google",
  "model":          "Pixel 8",
  "osVersion":      "14",
  "sdkInt":         34,
  "simOperatorName":"Orange F",
  "mcc":            "208",
  "mnc":            "01",
  "batteryLevel":   78,
  "isCharging":     false,
  "ramUsedMb":      312,
  "cpuLoadPercent": 18.4,
  "thermalStatus":  "NONE"
}
```

| Field | Description |
|-------|-------------|
| `manufacturer` | Device manufacturer |
| `model` | Device model name |
| `osVersion` | Android version string |
| `sdkInt` | Android API level |
| `simOperatorName` | SIM card operator name |
| `mcc` | Mobile Country Code (3 digits) |
| `mnc` | Mobile Network Code (2–3 digits) |
| `batteryLevel` | Battery % (0–100) |
| `isCharging` | Charging state |
| `ramUsedMb` | App process RAM usage (MB) from `Debug.MemoryInfo.totalPss` |
| `cpuLoadPercent` | System CPU load % — 200ms `/proc/stat` sample |
| `thermalStatus` | Device thermal state (API 29+): `"NONE"` / `"LIGHT"` / `"MODERATE"` / `"SEVERE"` / `"CRITICAL"` / `"EMERGENCY"` / `"SHUTDOWN"` |

**`thermalStatus` correlation:** High CPU load and elevated thermal status (`MODERATE`+) can reduce antenna performance. Cross-correlating thermal state with signal metrics helps distinguish device-induced QoE degradation from network-induced.

---

## `scores`

Composite quality scores computed from raw metrics.

```json
"scores": {
  "streaming": { "score": 92, "label": "Excellent" },
  "gaming":    { "score": 85, "label": "Excellent" },
  "rtc":       { "score": 88, "label": "Excellent" }
}
```

| Score | Inputs | Use case |
|-------|--------|---------|
| `streaming` | Download speed + latency | Suitability for video streaming |
| `gaming` | Latency + jitter + packet loss | Suitability for real-time gaming |
| `rtc` | Latency + jitter + packet loss | Suitability for VoIP / video calls |

Labels: **Poor** (score < 50) / **Core** (50–79) / **Excellent** (≥ 80).

---

## `mos`

Mean Opinion Score — voice call quality estimate (ITU-T G.107 E-model).

| Value | Quality | User perception |
|-------|---------|----------------|
| 4.3–5.0 | Excellent | Perfect clarity |
| 4.0–4.3 | Good | Occasional imperceptible distortion |
| 3.6–4.0 | Fair | Slight distortion, acceptable |
| 3.1–3.6 | Poor | Annoying, but usable |
| < 3.1 | Bad | Very annoying, unusable |

Computed from: `latencyMs` + `jitterMs` + `udpPacketLoss.lossPercent`.

---

## Null Field Behaviour

Fields are `null` (not absent) when:

| Reason | Fields affected |
|--------|----------------|
| Feature flag disabled (`enableSpeed=false`) | `speed`, `scores`, `mos` |
| No UDP host configured | `udpPacketLoss` |
| Permission denied (`ACCESS_FINE_LOCATION`) | `geo`, most `radio.*` fields |
| Android API < 29 | `radio.bandwidth`, `device.thermalStatus`, `neighboringCells` |
| Android API < 30 | `radio.sinr` |
| Technology mismatch (e.g. LTE has no `lac`) | `radio.lac`, `radio.psc`, `radio.bandwidth` |
| Network error during measurement | `dns.success=false`, `webBrowsing[].success=false` |

Backends should always handle `null` gracefully for every field.

---

## Recommended Backend Schema (PostgreSQL)

```sql
CREATE TABLE measurements (
    test_id          UUID PRIMARY KEY,
    timestamp        TIMESTAMPTZ NOT NULL,
    sdk_version      TEXT,
    device_id        TEXT,           -- derive from device fields if needed

    -- Speed
    download_mbps    FLOAT,
    upload_mbps      FLOAT,
    latency_ms       FLOAT,
    jitter_ms        FLOAT,
    loaded_latency_ms FLOAT,

    -- Packet loss
    udp_loss_percent FLOAT,
    udp_method       TEXT,

    -- Radio
    rsrp             INT,
    rsrq             INT,
    sinr             INT,
    rssi             INT,
    cqi              INT,
    cell_id          BIGINT,
    pci              INT,
    tac              INT,
    lac              INT,
    earfcn           INT,
    bandwidth_khz    INT,
    is_roaming       BOOLEAN,
    nr_mode          TEXT,           -- 'NSA', 'SA', null
    network_gen      TEXT,           -- '2G','3G','4G','5G','WiFi'
    signal_level     TEXT,

    -- DNS
    dns_resolve_ms   INT,
    dns_success      BOOLEAN,

    -- Network
    ip_address       INET,
    asn              TEXT,
    isp              TEXT,
    country_code     CHAR(2),
    cf_colo          TEXT,
    is_locally_served BOOLEAN,
    ip_version       TEXT,

    -- Location
    geo_lat          DOUBLE PRECISION,
    geo_lon          DOUBLE PRECISION,
    geo_accuracy_m   FLOAT,

    -- Device
    manufacturer     TEXT,
    model            TEXT,
    os_version       TEXT,
    sdk_int          INT,
    mcc              TEXT,
    mnc              TEXT,
    battery_level    INT,
    thermal_status   TEXT,
    cpu_load_pct     FLOAT,

    -- Scores
    mos              FLOAT,
    score_streaming  INT,
    score_gaming     INT,
    score_rtc        INT,

    -- Raw JSON (for web browsing, neighboring cells, social latency)
    web_browsing     JSONB,
    neighboring_cells JSONB,
    social_latency   JSONB,
    raw_payload      JSONB           -- full original payload
);

-- Spatial index for map queries
CREATE INDEX ON measurements USING GIST (
    ST_MakePoint(geo_lon, geo_lat)
) WHERE geo_lat IS NOT NULL;

-- Time-series index
CREATE INDEX ON measurements (timestamp DESC);

-- Operator analysis
CREATE INDEX ON measurements (country_code, mcc, mnc, timestamp DESC);
```
