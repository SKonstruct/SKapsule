# Changelog

Notable changes per release. Versions are the git tags the APKs are built from
(`vX.Y.Z` → `skapsule-vX.Y.Z.apk`).

## 3.0.0

### Added

- **Languages.** The app can be set to Arabic, German, Spanish, Estonian, French, Italian, Japanese, Polish, Brazilian
  Portuguese, Russian and Simplified or Traditional Chinese (sidebar > **Language**, or Android's per-app language
  setting); text not translated yet shows in English. The remaining fixed English texts moved into the string files.
- **A credit.** "Built with ♥ by SirDank" in the top-left corner of the main screen, quiet and grey with a
  pink heart; the name opens the author's site.
- **FPS counter.** A small "FPS 55" at the top centre, on by default. **Show FPS counter** in the
  in-game settings panel (the gear) turns it off.
- **Sharpening.** A slider in the same panel sharpens the picture on its way to the screen, which is what
  you want when **Resolution** is below 100%: the game is drawn smaller and stretched, and sharpening
  brings the edges back. 0% (the default) leaves the picture as it was.
- **Sharp HUD (experimental).** Below 100% resolution the HUD is drawn small and stretched too, so its text
  is soft. With this on, the scene stays at your resolution and the HUD is drawn at the screen's full
  resolution, so its text is crisp. The HUD is then smaller, so raise the game's own UI scale. Off by default.
- **Layouts: presets, export and import.** **Layouts…** in the Edit Controls panel keeps named presets of your
  control layout (plus the built-in default), and exports or imports one as a `.json` file through the system
  file picker. A layout exported here imports on SKapsule for iOS, and the other way round. Only the
  arrangement travels: where each control sits, its size and whether it shows, plus opacity and **Show Buttons**.
  Resolution and **Show Controls** stay yours, and a file can never change what a control does. Importing saves a
  new preset and asks before applying it.
- **Mods apply by themselves.** There is no Apply Mods button: when the mods folder changes (a download, or a
  mod you added) or the game updates, SKapsule applies the mods when you open the launcher, and restores the game
  once if you remove every mod. **Remove Mods** is now **Reset Game Files**; the mods already in the folder stay
  unapplied after it until the folder changes.
- **The game's own mouse cursor.** **Show mouse cursor** in the sidebar and in the editor's Settings tab shows
  Spiral Knights' real cursor, the one the game draws on desktop, where the game last saw the pointer. It follows a
  finger, a trackpad or an external mouse, and a mouse's own pointer is hidden while it shows, so there is only one.
  On by default.
- **Avoid screen edges in the editor.** The Settings tab has the same switch as the sidebar, and it re-insets the
  game at once.
- **A calmer touch-controls editor.** A tab strip at the top switches between **Edit** and **Settings**. On Edit,
  the small dialog for the selected control only appears when something is selected, and it docks away from
  that control so it never covers what you are dragging. Settings shows the full panel with the controls
  visible but untouchable.
  The tab strip is Edit | Settings: the settings button closes the editor.
- **A tidier sidebar.** The descriptions under **Send crash reports** and **Sharp HUD** are
  gone, and the settings sidebar runs to the bottom edge of the screen.
- **Floating thumb sticks.** Each stick gets a square pad and appears where your thumb lands inside
  it, following the thumb but never leaving the pad, with a faint ghost where it waits. A touch outside the pad
  reaches the game, and buttons over the pad still win it. Drag a pad anywhere in the editor. The fixed round sticks
  and the **Floating left stick** and **Floating right stick** switches are gone: this is the only kind.
- **An Attack button.** A hold button above **Defend**, the same primary attack as the right stick, for a thumb
  that would rather not use the stick. Layouts you already have get it at that spot.
- **Icons on the combat buttons.** Strafe, Defend, Attack, Dodge and Bash show an icon instead of text, in the
  game's colours: red for Attack, blue for Defend, light blue for Dodge and yellow (the stun colour) for Bash.
- **The main screen turns sideways.** It is no longer locked to portrait: in landscape the title and news sit on the
  left and the server, mode and **Launch** on the right.
- **Graphics settings in the sidebar.** **Show FPS counter**, **Sharpening** and **Sharp HUD** are now also in
  the Settings sidebar, so you can set them without starting the game. They are the same settings as the in-game panel.
- **The modpack button says what to do.** It reads **Get SirDank's Modpack**, **Update Modpack** when the modpack
  has changed, or **Modpack up to date**. SKapsule asks GitHub once when it starts.
- **Memory breakdown in Share Logs.** The once-a-minute memory line now splits the process into Java heap,
  native heap, graphics and code, so a long-session out-of-memory crash can be traced to the part that grows.

### Fixed

- **Typing in the game's text boxes works as you type.** The text showed up but did nothing (a search or filter did not
  update, a form did not enable) until Enter or Backspace was pressed. The on-screen keyboard now sends each
  character the way a desktop keyboard does, key press then the character then key release.
- **Changing Resolution while playing now resizes the game.** It used to keep drawing at the old size, in a
  corner of the screen, until you restarted.
- **Reset Layout puts Opacity back to its default (100%).** It landed on 52%.
- **Mods apply in a fixed order.** They used to apply in whatever order Android listed the files, so which mod won
  a clash could differ from phone to phone. The mods that change the most files now go first and equal ones by
  name, so a small, targeted mod wins over a broad one (the same rule as SKradle). There is nothing to sort by hand.

### Changed

- **Steam sign-in is a QR code, or your username and password.** The first Steam launch shows a code
  in portrait, before the game turns landscape: scan it with the Steam app on your phone, or tap **Open Steam app** if Steam is on the same device, and
  approve. With no second device to show the code to, tap **Use username and password** and enter your Steam Guard
  code when asked. Later launches sign in on their own. The password is only used to sign in; SKapsule keeps just
  the sign-in token. Cancel the dialog to carry on without Steam.
- **The events on the home screen slide.** Instead of fading from one to the next, the card slides every 8 seconds
  and you can swipe between events; dots show how many there are and which one you are on.
- **A new default touch layout.** The buttons are laid out on a grid (the sticks mirror each other, the right cluster
  is two columns, the bottom row has one even spacing), the sticks float, and opacity starts at half of the slider
  (it was 40%, then full). Every button, ESC included, is a fifth smaller. **Reset Layout** and new installs get it;
  layouts you already saved keep their positions and opacity, and the smaller buttons apply to them too.
- **Reset Layout tells you when the default changed.** It reads **Reset Layout ● New default** until you reset (or
  load a preset or file), so you can try the new controls when you like.
- **Show Buttons starts off every time.** Each start of the app begins with only ESC and the sticks showing; the eye
  button or the switch brings the buttons back for that session. The choice is no longer saved, and importing a layout
  does not change it.
- **The Steam sign-in dialog uses the SKonstruct colours** (from the brand tokens), including the new
  username and password form.
- **The settings, keyboard and eye buttons match the other buttons.** They use the same translucent fill and the same
  opacity, including the boost while you arrange controls. Their default spot moved to the top, level with ESC.
- **The control editor's dialog is always at the top right**, below the Done button in the standalone editor.
- **Open Folder goes straight to the Files app**, on the game folder, instead of asking which app to open it with.
- **The default memory limit for the game is now 1024 MB** (it was 768 MB). It applies if you never changed
  the setting, and it is only a ceiling: the game still starts small and takes what it needs.
- **The game now runs with two more of KnightLauncher's recommended settings.** Its own requests to run
  garbage collection are ignored, which avoids some freezes, and textures it no longer uses are released
  sooner.
- The graphics and audio libraries are updated to their latest upstream versions (rendering fixes and
  new OpenGL features, and many OpenAL Soft changes).

## 2.4.6

### Changed

- A report that the game stopped now says which error stopped it, so the next one can be diagnosed
  without asking for a log. The dialog you see is unchanged.

### Fixed

- **Game froze when opening the friends list with Steam sign-in.** The game's 10 November update added a
  Steam call the Steam stand-in did not have, which stopped the game's render loop. The friends list now
  opens. If the game does stop after it has started, SKapsule now says so and offers to share the log or
  exit, instead of leaving a frozen screen.

## 2.4.5

### Added

- **Connect to custom servers.** A **Server** picker above Web / Steam lists Official and the servers you
  add: tap it to choose one, and use the **+** beside it to add a server (name, address, port, and
  optionally the server's public key, client and content URLs and a username and password for quick
  login) or the pencil to edit or delete the selected one. The login is encrypted with the Android
  Keystore and only handed to the game if the game is set up for that server. The chosen server is
  written into the game's `config.jar` at every launch, so switching back to Official undoes it. A
  private server uses the game's own logon window unless you saved a login for it, so the Web / Steam
  switch is hidden while one is selected: Steam sign-in and the saved web account stay with Official. If
  the game has an update pending, the launcher asks you to update on Official first, because the updater
  would otherwise replace the edit and quietly connect you to Official. On Android 17, a server on your
  own network (a PC at home, for instance) needs **Local network** access, which SKapsule asks for the
  first time you play on one.

### Fixed

- **A dropped connection while downloading an update or the mods** now says to check your connection,
  instead of showing the raw network error, and is no longer sent as an error report.

## 2.4.4

### Changed

- **Built for Android 17** (target and compile SDK 37, up from 35) and the latest AndroidX libraries.

### Fixed

- **Mods can no longer replace the behavior, spawn table and variant table configs** (`config/behavior`,
  `spawn_table`, `variant_table`, `.dat` and `.xml`). The protected list was still six entries short of
  KnightLauncher's.
- **Apply Mods and Remove Mods before the game's first launch, or with no mods downloaded,** explain
  what to do first and no longer send an error report for it.
- **Applying or removing mods could crash** if the screen was recreated (for example by rotating) while
  it ran: the progress dialog was dismissed after its window was gone.
- **A stalled game start with no connection** is no longer sent as an error report; the message to
  check the connection stays.
- **A runtime that cannot be unpacked because a bundled file is unreadable** now suggests
  reinstalling, and the report records the device's CPU architectures.

## 2.4.3

### Fixed

- **Mods were undone every time the game started** (a 2.4.2 regression). 2.4.2 edited the game's
  `getdown.txt` to use https, and that file is checked against a published hash: the check failed on
  every launch, the game's updater refetched its files and re-downloaded or re-unpacked everything a
  mod had changed. The file is left as the server wrote it again. After updating, press Apply Mods
  once more.
- **The https change in 2.4.2 is withdrawn.** It did not keep the updater on https after its first
  check (downloads went over http either way), so it protected nothing, and it caused the bug above.
- **Mods whose `mod.json` ends with a trailing comma** (several in the modpack) are read again, so
  their name and type are no longer lost.

## 2.4.2

### Added

- **Web account**: save your Spiral Knights username and password (Settings → Web account) and web
  launches sign in automatically. It is stored encrypted with a key in the Android Keystore, never
  in plain text; Remove deletes it. Your own `-Dusername=` / `-Dpassword=` lines in `sk/extra.txt`
  are honoured too (only `-D` properties can still apply once the game's Java is running).

### Security

- **Mod archives can no longer write outside the game folder.** Locale bundle names from a mod's
  `mod.json` are confined like file entries, and a failed jar swap now stops the apply instead of
  being skipped.
- **The Files-app folder is limited to your mods.** The document provider only exposes `sk/mods`
  (not the login token, home or code folders), and rejects names with `/`, `.` or `..`.
- **The game downloads over HTTPS.** The launcher fetches Getdown's manifest and rewrites its
  `appbase` to `https://`; the game's digest is still not signature-checked (no signing certificate
  is published).
- **Modpack files are verified.** Each download is checked against the listing's SHA, only
  `raw.githubusercontent.com` is accepted, and a sync removes only files an earlier sync added.
- **Update links are only followed when they point at github.com over HTTPS.** The link-opening
  relay between the game and the launcher checks the caller, line length and scheme.
- **Share Logs and crash reports are scrubbed** (tokens, `Authorization`, session ids, paths, emails)
  in log text and exception messages. The wording no longer calls the reports anonymous.
- Release workflows pin every action to a commit, drop credentials after checkout, delete the
  keystore as soon as the APK is signed, and the Gradle wrapper is checksum-verified.

### Fixed

- **Mouse buttons are tracked separately.** Releasing the left button and quickly pressing the right
  used to be delayed or wrong, because a mouse is a single pointer and the second button never
  arrived as a press of its own. Charging an attack, letting go and shielding now registers each
  button as it happens.
- **Avoid screen edges no longer crops the top and bottom.** It keeps the game clear of the camera
  cutout and rounded corners on the left and right only.
- **A hardware keyboard, fold or font change no longer restarts the game screen** while the game
  runs, and the game screen is not re-staged when the game is already up.
- **A stalled or failed game start says so.** The launcher shows the reason with Share Logs and
  Keep waiting, instead of staying on "Validating". A crash dialog appears once per crash.
- **Installing the game or mods while the game is running is refused**, and a second install
  cannot start while one is in progress.
- A damaged game jar is detected and repaired instead of failing at launch.
- `getdown.txt` is written atomically and checked, so a killed launch cannot leave a broken one.
- **A timed-out or offline Steam sign-in no longer discards your saved login.** Only Steam
  rejecting the token does.
- Version comparison ignores suffixes such as `-rc1`.

- **A fresh update could show in yellow as a "development build"** (`SKapsule v2.4.1-dev`). The launcher
  remembers GitHub's latest release for six hours, and kept the old answer across an update, so
  right after installing a new release it could still believe an older one was the latest. The
  remembered answer now belongs to the installed version, and Check for Updates repaints the title.

## 2.4.1

### Added

- **Check for Updates** in the options sidebar asks GitHub right away (the automatic check can
  be up to six hours old) and always answers: update available, up to date, or couldn't check.

### Fixed

- **2.4.0 could not be installed over an existing install** ("INSTALL_FAILED_VERSION_DOWNGRADE"):
  its internal version number came from a CI run counter that restarted at 1, below 2.3.0's 44.
  The number is now derived from the version itself (2.4.1 is 20401), so it can only go up, and
  the release workflow refuses a version that is not newer than the latest release. If you
  installed 2.4.0 fresh, 2.4.1 updates it normally.

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
