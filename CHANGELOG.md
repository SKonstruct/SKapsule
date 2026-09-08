# Changelog

Notable changes per release. Versions are the git tags the APKs are built from
(`v2.2.1` → `skapsule-v2.2.1.apk`).

## 2.2.1

### Fixed

- News card never appeared: announcements with no expiry (which is most of them) were
  discarded by the countdown window check.
- Steam auth tickets were sized against the buffer's remaining space rather than its
  capacity, so a ticket that fit could be rejected and the game would never finish its
  server logon.

- **Touch buttons were unusable while a joystick was held** ([#43](https://github.com/SKonstruct/SKapsule/issues/43)).
  The overlay hit-tested every pointer at the *first* finger's position, so with a
  thumb on the move stick no button could be pressed, and releases were delivered to
  whichever control held that first pointer, leaving buttons stuck down. Each pointer
  is now routed independently to the control it landed on.
- Held controls are released when the layout is rebuilt, edit mode is toggled, or the
  controls are switched off, instead of latching.
- Hardware keyboards: arrow keys reached the game, modifiers (Shift/Ctrl/Alt) are
  reported, and F-keys, the numeric keypad, Meta and the lock keys are mapped.
- Gamepad triggers no longer rest half-pressed when a controller is connected.
- Mod archives are contained to the extraction directory (Zip Slip), mod downloads are
  atomic, and the document provider cannot be walked outside the app's own files.

### Changed

- Minimum render scale is now 0.6 (and the new default). At 0.5 a 1080p-tall device
  rendered a 540 px surface and Spiral Knights' own UI stopped laying out, which is why
  character select never appeared. Existing layouts are clamped up on load.
- The bundled runtime is re-unpacked whenever the app version changes, so an update
  always replaces the staged JRE and LWJGL rather than keeping the previous unpack.
- Default game preferences now also seed `interface_gain=1.0`.

### Added

- **Edit Controls** in the options sidebar: arrange the on-screen controls without launching
  the game.
- **News card** on the home screen showing the current in-game announcement, from the
  same feed KnightLauncher uses. Tapping it opens the announcement.
- **In-app updates**: the update banner now downloads and installs the new APK instead
  of only linking to the release, with release notes and "skip this version".
- **Max RAM** control in the options sidebar, with the device's free and total memory.
- **Crash reporting** to Sentry, on by default with a switch in the sidebar. Anonymous,
  and the logcat dump is scrubbed of credentials and account emails.
- Player count now includes the Preview server; tapping the subtitle explains how the
  estimate is calculated.

### Changed

- Bottom action-row buttons are labelled 1–7 with a gap between attacks and items.

## 2.2.0

- Repositionable touch controls with per-control visibility and scale, an opacity
  slider and a resolution slider.
