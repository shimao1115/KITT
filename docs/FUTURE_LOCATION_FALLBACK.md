# Future Task — Location Fallback and Position Confidence

Status: **BACKLOG — do not implement now**

## Goal

Improve 沿途 positioning robustness when GNSS/GPS is weak, without ever letting VPN/proxy/IP geolocation move the journey to the wrong country or city.

## Product rule

> GPS/GNSS is preferred when reliable. If satellite positioning is temporarily weak, use Android device/network/fused positioning as a coarse fallback. Never use public-IP geolocation as the journey location source.

## Desired behavior

When a journey is running:

1. Prefer precise GNSS/GPS fixes when fresh and accurate.
2. If GPS is temporarily unavailable or too weak:
   - allow Android network / fused location sources (cell tower, Wi‑Fi and system-assisted positioning) as a fallback;
   - keep the journey moving at coarse regional accuracy rather than dropping location entirely.
3. When good GPS returns:
   - automatically switch back to GPS without user action.
4. Carry location quality/source into Context so Director can adjust claims:
   - precise GPS can support nearby/proximity logic;
   - coarse network fixes can support district/town-level context;
   - coarse fixes must not justify exact distance, “right beside you”, visibility, or precise landmark triggering.

## VPN / proxy boundary

The user's phone may run a proxy/VPN/TUN and the public IP may resolve to another country.

Therefore:

- **Do not use IP geolocation for journey positioning.**
- Do not infer location from proxy exit country/city.
- VPN/proxy status may be used for network diagnostics only, never as location truth.
- Android Location APIs / system fused positioning remain the location source.
- Mock-location/developer providers should remain explicitly detectable and separate from real positioning.

## UI idea

Expose a compact positioning status, e.g.:

- GPS · ±8 m
- Network/fused · ±250 m
- Waiting for precise GPS
- Location unavailable

This can later share a small diagnostics surface with network-health status, but should not create a complex map/debug UI.

## Engineering direction

Current production uses `LocationManager.GPS_PROVIDER` directly. Future work should evaluate:

- Android fused location when available;
- network provider fallback where appropriate;
- freshness and accuracy thresholds;
- source switching/hysteresis to avoid jitter;
- preservation of current privacy boundary (no raw track persistence).

Do not assume a fused provider exists on every OEM/device; keep graceful fallback.

## Acceptance

Real-phone tests should cover:

- outdoor good GPS;
- indoor/weak GPS with cellular/Wi‑Fi available;
- tunnel/temporary GNSS loss and recovery;
- VPN/proxy enabled with foreign exit IP;
- airplane/network loss;
- coarse location must not trigger precise landmark narration;
- GPS recovery must seamlessly restore precise positioning;
- no use of IP geolocation in journey logic.

## Boundary

Do **not** implement during the current V0.3.2 field-use window. Revisit after the current trip/field test.
