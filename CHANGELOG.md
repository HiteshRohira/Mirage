# Changelog

Earlier release history is preserved in [the archive](docs/archive/upstream-changelog.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.16] — 2026-11-06

- Bundle the GPL-3.0 and LGPL-2.1 texts and third-party notices in the APK.
- Add glass materials to the launcher and side sheets, with a window-scoped
  asynchronous panel backdrop and reusable focus drawables.
- Simplify the phone remote to a direction pad and remove the touchpad.

## [1.1.15] — Local build, 2026-10-30

- Resolve AirPlay media-playlist segment, key and init-map URLs against their
  original remote base, including cached playlists.
- Report playback state as plist booleans, acknowledge actions with a binary
  plist and publish video events on timeline changes.
- Accept optional query parameters on the local HLS master endpoint.

## [1.1.13] — 2026-10-29

- Keep Safari's AirPlay reverse connection open after acknowledgements.
- Harden mirroring decoder cleanup and keep receiver health alarms on schedule.
- Add the remote Settings button overlay (1.1.11) and Wi-Fi/Inputs sheets (1.1.12).

## [1.1.10] — 2026-10-28

- Limit drawer fading layers to edge bands and cache the moving drawer surface.
- Prepare and retain Control Center views, avoid redundant tile text updates,
  and retain Bluetooth profile connections while panels are hidden.
- Cache Bluetooth status and skip redundant blur for ambient gradients.
- Add disabled-by-default bounded Home, panel, remote-input, and receiver timing
  diagnostics with no uploads or typed-text capture.
- Add bounded mirroring presentation scheduling and adaptive audio headroom;
  preserve decoder/session-reset behavior. Media quality remains unverified
  against the proposed timing targets.
- Publish matching ARMv7 and ARM64 standalone APKs with the existing package
  and signing identity for normal in-place updates.
- Document seven-run physical-TV rendering results and the remaining first-use,
  physical input-response, network-recovery, and direct QuickTime casting gaps.

## Earlier source changes

- Use the Mirage name across the launcher, TV banner, phone remote and AirPlay fallback.
- Change the Android application ID to `tv.mirage.launcher`; existing installations require a separate setup.

- Remove the inherited website, social-card template and upstream release publisher.
- Refresh build/remote/interface documentation and remove stale build commentary.
- Side-panel focus containment is staged separately on `fix/side-panel-focus`; testing is deferred.

## [1.1.7-airplay.8] — 2026-10-26

Source: `fix/review-backlog` at `cd46f2b`; installed reference build, version code 23.

- Per-phone pairing tokens, rotating codes, pairing limits, revocation and an off switch.
- Hardened native HTTP/HLS handling, JNI thread cleanup and selected UxPlay backports.
- Bounded mirroring queues, audio discontinuity handling and session-only resource locks.
- Persistent phone key injection and keyboard recovery after process interruption.
- Launcher refresh/cache fixes, in-place status updates and preserved app order.
- Optional ARMv7-only packaging via `-PtvAbiOnly`.

## [1.1.6-airplay.7] — 2026-10-24

Source: `main` before source cleanup, version code 22.

- Integrated AirPlay receiver and player; separate receiver app no longer required.
- Low-latency audio default, asynchronous mirroring decoder and HLS compatibility fixes.
- TV-hosted phone remote and native Control Center/Appearance interface.
