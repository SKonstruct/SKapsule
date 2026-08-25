# Changelog

Notable changes per release. Versions are the git tags the APKs are built from
(`v2.2.1` → `skapsule-v2.2.1.apk`).

## 2.2.1

### Fixed

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

### Added

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
