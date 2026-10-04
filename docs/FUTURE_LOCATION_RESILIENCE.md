# Future Task — Resilient Location + Network Diagnostics

Status: **BACKLOG — do not implement now**

## Why

Current 沿途 real location source primarily listens to Android `GPS_PROVIDER`. On the road, GNSS can weaken or disappear temporarily in urban canyons, tunnels, mountains, bad sky view, or during cold start.

The product should degrade gracefully instead of treating GPS loss as total location loss.

## Product rules

### 1. Location truth hierarchy

Prefer, in order:

1. fresh high-accuracy GNSS/GPS;
2. Android system/fused or `NETWORK_PROVIDER` location (cellular + Wi-Fi assisted where available);
3. a recent trusted last location with clearly increasing uncertainty;
4. unknown location.

Never use public-IP geolocation as the journey's physical location truth source.

VPN / proxy / Clash / TUN may move the public IP to another country. This must affect only network diagnostics, never the user's journey position.

### 2. Wi-Fi assistance

Wi-Fi can help positioning when the Android location stack / network provider has a positioning database available.

Do not build a custom global BSSID → coordinate database.

Raw Wi-Fi scan results alone do not provide absolute coordinates. Treat them only as:
- input to a supported system/fused location provider;
- diagnostics / local context if a future feature explicitly needs them.

Respect Android runtime permissions and scan throttling. Do not poll aggressively.

### 3. Bluetooth assistance

BLE scanning alone does not provide absolute geographic coordinates.

Use Bluetooth for positioning only when there is a known-location beacon/device or a clearly bounded proximity use case.

For the general road-trip location pipeline, do not scan arbitrary BLE devices and pretend they locate the phone.

If a future known-beacon integration exists, Bluetooth proximity may complement GPS/network location, but must carry its own source/accuracy semantics.

### 4. Location source and accuracy must be explicit

Each accepted fix should carry enough metadata for downstream reasoning, e.g.:
- source: GPS / NETWORK / FUSED / LAST_KNOWN;
- horizontal accuracy;
- age;
- whether precise or approximate.

Director/trigger logic must become more conservative as uncertainty grows.

Examples:
- GPS ±8 m: landmark proximity may be precise.
- network estimate ±300 m: acceptable for district/town chapter context, not for “you are at the gate” claims.
- stale last-known: only coarse continuity, never exact landmark triggering.

### 5. Smooth fallback / recovery

Desired behavior:

> GPS healthy → use GPS.
> GPS weak/lost → fall back to system/network location without interrupting the journey.
> GPS returns → automatically promote back to GPS.

Avoid sudden impossible jumps when switching providers. Use timestamp, accuracy and plausibility checks.

### 6. VPN / proxy isolation

Hard rule:

> Network route and physical location are separate domains.

Do not let:
- public IP country;
- VPN exit node;
- proxy server geography;
- DNS resolver geography

override GPS/network/fused physical location.

### 7. Network-status UI (future small UX feature)

Add a compact user-visible network health indicator.

Passive state may show:
- Android network connected / validated;
- Wi-Fi vs cellular;
- VPN/TUN present;
- recent ChatGPT request status;
- recent Director latency;
- recent research latency/error.

Suggested simple states:
- green: service path recently healthy;
- yellow: network exists but recent ChatGPT/search request was slow/interrupted;
- red: no usable network/service connection.

Tapping “网络诊断” may run one lightweight active probe. Do not continuously ping at high frequency.

This network indicator is diagnostic only and must not infer physical location from IP.

## Android implementation direction

Prefer the least-complex reliable route:

- evaluate Android system location providers / fused location if reliably available on the user's vivo;
- otherwise combine `GPS_PROVIDER` and `NETWORK_PROVIDER` through a small source-selection layer;
- do not add Google-only dependencies unless they are proven reliable on the actual device;
- preserve current GPS path and existing area resolver behavior.

Manual Wi-Fi/BLE scanning is not the first implementation choice for absolute positioning.

## Acceptance

Real-phone tests should include:
- outdoor GPS healthy;
- GPS temporarily unavailable;
- cellular data only;
- Wi-Fi on/off;
- VPN/TUN on with a foreign exit;
- transition GPS → network fallback → GPS;
- no jump to VPN exit country;
- source and accuracy visible in diagnostics;
- chapter-level context continues under coarse location;
- precise landmark triggers are suppressed when uncertainty is too large;
- no excessive battery/network scanning.

Do not implement during the current V0.3.2 field-use window.
