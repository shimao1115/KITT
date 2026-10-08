# Hotfix — GPS + Network Location Fallback

Status: **PRIORITY — fix in the next field-use build**
Origin: real-driving feedback, 2026-10-08.

## Problem

The current real-location path relies too heavily on GPS/GNSS. In mountains, tunnels, urban canyons, bad sky view, cold start, or weak-signal conditions, 沿途 can lose useful realtime position even though Android may still have a coarse location from cellular/Wi-Fi assisted positioning.

This must be fixed before relying on 沿途 for continued field use.

## Product requirement

Physical position must use Android device location sources, never public-IP geolocation.

Desired source hierarchy:

1. fresh, high-quality GPS/GNSS;
2. Android system/fused or `NETWORK_PROVIDER` location (cellular + Wi-Fi assisted where available);
3. recent trusted last-known fix for a short bridge, with increasing uncertainty;
4. unknown.

The app should automatically fall back and recover:

> GPS healthy → GPS  
> GPS weak/lost → network/fused location  
> GPS returns → promote back to GPS

No manual mode switch should be required.

## Hard VPN / proxy rule

The user normally runs a VPN/proxy/TUN while travelling.

**VPN network geography must never influence journey position.**

Do not use:
- public IP address;
- GeoIP;
- VPN exit country;
- proxy server location;
- DNS resolver geography;
- search-engine inferred location

as physical-location input.

A VPN exit in the United States must not move the vehicle to the United States.

Network route diagnostics and physical location are separate domains.

## Location-fix contract

Every accepted fix should carry at least:
- latitude / longitude;
- source: GPS / NETWORK / FUSED / LAST_KNOWN;
- horizontal accuracy;
- timestamp / age.

Selection policy should prefer the newest plausible high-quality fix rather than blindly preferring provider name.

Examples:
- GPS ±8 m: eligible for precise landmark proximity;
- network/fused ±100–500 m: suitable for district/town/street context, not “you are at the gate” claims;
- stale last-known: only coarse continuity.

## Plausibility and handoff

When switching providers:
- reject impossible jumps;
- compare timestamp, distance, speed and reported accuracy;
- do not oscillate rapidly between sources;
- recover to GPS automatically when a trustworthy GPS fix returns.

Do not smooth so aggressively that a moving car appears stuck.

## Android implementation direction

Prefer the least-complex reliable Android path.

1. Preserve existing GPS listener.
2. Add `NETWORK_PROVIDER` updates when available.
3. If a stable fused/system location path is already available on the target vivo without adding a fragile Google-only dependency, it may be evaluated.
4. Put provider arbitration behind one small location-source selector so downstream Journey/Director code receives one accepted physical fix plus metadata.
5. Do not implement custom cellular-tower databases, Wi-Fi BSSID databases, or BLE positioning.

“基站定位” here means Android's network-assisted location path; the app should not try to calculate coordinates from raw cell IDs itself.

## Runtime behavior

- chapter-level narration may continue from a coarse network fix;
- precise landmark triggers should be suppressed when accuracy is insufficient;
- UI/diagnostics should be able to show location source and accuracy for testing;
- losing GPS alone must not stop Journey if a usable network fix exists.

## Real-phone acceptance

Test on the user's vivo with the normal VPN/TUN configuration enabled.

Required cases:

1. Outdoor GPS healthy → source resolves to GPS.
2. GPS becomes weak/unavailable → app continues to receive usable network/fused fixes where Android provides them.
3. GPS returns → app promotes back to GPS without restart.
4. VPN/TUN exits through a foreign country (including United States) → physical location remains the phone's actual local position.
5. Wi-Fi off, cellular data on → coarse fallback is tested.
6. Wi-Fi on → assisted fallback is tested.
7. Network fix with coarse accuracy does not trigger precise “arrived at landmark” narration.
8. No impossible long-distance jump appears during provider handoff.
9. Existing journey, Director, research and TTS regressions remain passing.

## Non-goals

Do not:
- infer physical location from IP;
- build a cellular tower database;
- add continuous Wi-Fi/BLE scanning;
- add a new map SDK merely to solve this hotfix;
- redesign Journey/Director;
- bundle this with offline knowledge packs.

This is a focused field-reliability fix.
