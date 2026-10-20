# Changelog

Notable changes per release. Versions are the git tags the APKs are built from
(`v2.4.0` → `skapsule-v2.4.0.apk`).

## 2.4.0

### Added

- **Donate button** on the home screen, in Buy Me a Coffee's yellow with its cup glyph,
  opening the project's page. Entirely optional.
- **Game Mode**: the app declares itself a game, so Android 12+ shows the Game Dashboard
  and applies its game optimisations. The platform's resolution downscaling is opted out
  of, because it resizes the backbuffer behind the app and Spiral Knights' UI stops laying
  out below ~578 px of surface height; FPS override stays on.
- **Crash diagnostics.** Share Logs now includes what Android's log forgets within minutes: the
  game's output (kept across sessions), JVM crash reports (`hs_err`), garbage-collection and
  memory logs, and why Android last ended each process (Android 11+). The game's JVM now logs
  its exit code and leaves cleanly.
- **Boot failures are shown.** If the game cannot start, or its start-up status does not change
  for three minutes, a message appears with Share Logs and Exit (and Keep waiting, for a boot that
  is only slow), instead of the overlay staying on its last status forever.
- Steam sign-in tells you what it is doing ("Connecting to Steam…", "Signed in to Steam") and
  when it gave up.
- The bug report form asks for the GPU and graphics settings.

### Fixed

- **Controls jumped after being moved** in Edit Controls (reported on a Galaxy Fold 5): the
  position was applied twice, once while dragging and again at the next layout, so a button
  moved away from where it was dropped and could not be grabbed until a layout reset.
- **A mouse only moved the cursor while a button was held.** Hovering now moves it.
- **The on-screen buttons could take keyboard focus**, so Space or Enter on a hardware keyboard
  could press the gear instead of reaching the game.
- **The game could hang on "Starting Java runtime" with no explanation** when its files were
  damaged or out of date. The startup jars are checked and repaired before every launch,
  `sk-bootstrap.jar` is replaced on every launch, and damaged files send you back to the
  launcher to be repaired. A damaged game config jar is re-downloaded, and unpacked game
  resources that have gone missing are extracted again, which are the usual reasons for a game
  that stalls on "Validating".
- **Quitting could end in a native abort** that Android reported as a crash (the JVM's exit
  ran while the graphics threads were still alive). The exit now leaves the process cleanly.
- **Steam sign-in gave up while you were fetching your phone**: the whole attempt had only 60
  seconds, including connecting. It now has 45 more for connecting, and the key that protects
  your saved Steam login is saved immediately, so closing the game right after approving no
  longer loses it.
- **News card vanished again** when the feed added an `events` list: the service writes a
  trailing comma in each event, which Android's JSON parser rejects outright (KnightLauncher's
  tolerates it). The card now reads the feed as KnightLauncher 2.8 does — running and
  upcoming events, or the default event — and rotates through them every 8 seconds.
- **Removed class and locale mods stayed in the game.** They are merged into
  `code/config.jar` and `code/projectx-config.jar`, which were never reset. Both are now
  restored to the game's own copies, verified against the install's getdown digest, before
  mods are applied and when they are removed.
- **Standalone mods could replace protected configs.** The filter only ran for mods inside a
  modpack. The protected list now matches KnightLauncher's (it was missing seven configs the
  game ships and listed six it does not).

## 2.3.0

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
- Mouse scroll wheel now reaches the game — `EV_SCROLL` had no producer, so inventory and
  chat lists could not be scrolled. Right and middle clicks are no longer reported as left
  clicks.

### Changed

- The chrome row is three equal icon squares (settings, keyboard, eye) instead of two mixed
  text buttons, and sits beside ESC: **ESC, Settings, Keyboard, Eye**. Saved positions are
  reset once so the new row is visible.
- "Show Action Bar" is now **Show Buttons**, defaults to off, and hides every button except
  ESC; the joysticks and the chrome row stay. The eye button toggles it directly.
- **Show Controls** now hides the keyboard and eye buttons along with the controls. The
  settings button stays, since it is the only way back into the editor.
- On-screen buttons are the same size as the chrome icon buttons; they were noticeably
  larger. Every button carries a hairline outline, matching the iOS build.
- Touch controls start at 40 % opacity, and the slider now bottoms out at 20 % instead of
  fully invisible. A saved value below that is raised on load.
- Bottom action-row buttons are labelled 1–7 with a gap between attacks and items.
- Minimum render scale is now 0.6 (and the new default). At 0.5 a 1080p-tall device
  rendered a 540 px surface and Spiral Knights' own UI stopped laying out, which is why
  character select never appeared. Existing layouts are clamped up on load.
- The bundled runtime is re-unpacked whenever the app version changes, so an update
  always replaces the staged JRE and LWJGL rather than keeping the previous unpack.
- Default game preferences now also seed `interface_gain=1.0`.

### Fixed

- News card never appeared. Two separate causes: announcements with no expiry (which is
  most of them) were discarded by the countdown window check, and any announcement whose
  `announceType` was `"0"` was skipped outright — which the live one is. Neither is a gate
  in KnightLauncher, so the card now shows whenever the feed carries a complete
  announcement, and the timestamps only choose the chip: "Starts in", "Ends in", "Ended".
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
- Crash reporting no longer dies at startup on Android 8.x: it called `longVersionCode`,
  which is API 28, and the resulting `NoSuchMethodError` was not caught.
- Mod archives are contained to the extraction directory (Zip Slip), mod downloads are
  atomic, and the document provider cannot be walked outside the app's own files.

## 2.2.0

- Repositionable touch controls with per-control visibility and scale, an opacity
  slider and a resolution slider.
