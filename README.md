# SKapsule

An unofficial Android (arm64) port of **Spiral Knights**.

[![Tests](https://github.com/SKonstruct/SKapsule/actions/workflows/test.yml/badge.svg)](https://github.com/SKonstruct/SKapsule/actions/workflows/test.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
![Android 8.0+ · arm64](https://img.shields.io/badge/Android-8.0%2B%20%C2%B7%20arm64-lightgrey.svg)

SKapsule runs the _real_ Spiral Knights desktop client on your phone or tablet. It
ships a custom JRE and a set of native libraries, then boots the game's own Java VM
on-device — the game code and assets are downloaded and patched from the official
servers at runtime (via getdown), exactly like the desktop client. Nothing about the
game itself is bundled in this repository.

## Highlights

| | |
| --- | --- |
| **Play** | The real desktop client on your device. Gamepad-first, with on-screen touch controls, and a keyboard and mouse when you have them. Sign in with **Web**, or with **Steam** by QR code: there is no Steam password to type. |
| **Controls** | **Edit Controls** in the launcher moves, resizes and hides every control, with opacity and resolution. Keep named presets, or export and import a layout as a file that also works on [SKapsule for iOS](https://github.com/SKonstruct/SKapsule-iOS). |
| **Servers** | Official or your own private servers, saved in one picker. |
| **Mods** | Download the maintained modpack from the options sidebar; mods apply by themselves when the folder changes, and **Reset Game Files** puts the game's own files back; the button says **Update Modpack** when the pack changed. Mods apply in an automatic order, the broadest first, so a small mod wins over a big one. There is nothing to sort by hand. |
| **Graphics** | A resolution slider, **Sharpening**, **Sharp HUD** (experimental) and an FPS counter; the last three are also in the options sidebar. |
| **Languages** | English, Arabic, German, Spanish, Estonian, French, Italian, Japanese, Polish, Brazilian Portuguese, Russian and Simplified and Traditional Chinese. See [Translating](#translating). |
| **Updates and logs** | A banner offers new versions and installs them. **Share Logs** bundles a log with credentials and emails removed, including a memory breakdown for long-session crashes. |

---

## Disclaimer

Spiral Knights is © Grey Havens, LLC. **This is an unofficial, fan-made port
and is not affiliated with, endorsed by, or supported by Grey Havens.**

- No Spiral Knights game code or art assets are included in this repository or in the
  APK. They are downloaded from the official servers at first launch, the same way the
  official desktop launcher works.
- You need your own Spiral Knights account (web or Steam) to play.
- This project exists to let people play a game they already own on hardware the
  official client doesn't target. Please support the game through official channels.

---

## Controls

Gameplay is **gamepad-first**. Touch controls are in place; they are newer than the gamepad
path and still getting fine-tuning. Every finger is routed to the control it lands on, so
buttons work while a joystick is held (fixed after
[#43](https://github.com/SKonstruct/SKapsule/issues/43) — before that, holding the move
stick made every button unpressable).

- **Gamepad or Touch** — primary input for gameplay (movement, combat, menus).
- **Keyboard** — used as needed for text entry (login, chat). An on-screen
  **Keyboard** button is provided for devices without a physical keyboard.

---

## Installing (players)

1. Grab the latest signed `skapsule-vX.Y.Z.apk` from the
   [Releases](../../releases) page.
2. Copy it to your arm64 Android device (Android 8.0 / API 26 or newer) and install,
   allowing installation from unknown sources if prompted.
3. Launch **SKapsule**. On first run it unpacks the bundled JRE and runtime, then
   downloads/patches the game from the official servers — this first launch takes a
   while and needs a network connection.
4. Pick **Web** or **Steam**, tap **Launch** and sign in with your own account. Steam shows a QR code on the
   first launch, in portrait before the game turns landscape: scan it with the Steam app, or tap **Open Steam app** if Steam is on this device, and
   approve. There is no Steam password to type.

Later releases install themselves: when a newer version is published the launcher shows an
**Update available** banner, and tapping it downloads the APK and hands it to the system
installer (grant "install unknown apps" once when prompted). The dialog also offers release
notes and a **Skip this version** option.

Only `arm64-v8a` devices are supported (no 32-bit builds).

**Custom servers.** The **Server** picker above Web / Steam chooses Official or a saved server; the **+**
beside it adds one (address, port, and optionally its public key and client/content URLs) and the pencil
edits the selected one. The Web / Steam switch is Official's, so it is hidden for a private server. At launch the launcher writes the
connection settings into the game's `code/config.jar` (`deployment.properties`: `server_host`,
`server_ports`, `datagram_ports`, `key.public`, `client_root_url`, `content_url`), as [KnightLauncher](https://github.com/lucasluqui/KnightLauncher) does.
Official's values are written back when you switch, so nothing lingers. A private server gets the game's
own logon window, or your saved login for it if you gave one (encrypted with the Keystore; the game's bootstrap
drops it unless the game's `server_host` is that server). Steam sign-in and the saved web account are never sent to it. With a game update
pending the launch is refused until you update on Official, since the updater would replace the edit.
On Android 17 a server on your own network (private or `.local` addresses) needs the **Local network**
permission, which SKapsule requests when you launch against one; internet servers don't.

---

## Building from source (developers)

The APK is a _full from-source_ build: every native component is compiled from its
submodule, staged into the launcher, and then the Android app is assembled. CI does
exactly this — see [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)
for the canonical, reproducible recipe.

### Prerequisites

| Tool            | Version                 | Used for                                                                    |
| --------------- | ----------------------- | --------------------------------------------------------------------------- |
| Android SDK     | compileSdk 37, targetSdk 37 | building the app                                                        |
| Android NDK     | `30.0.14904198`         | native libs (Clang 20; NDK 27's Clang 18 miscompiles OpenAL's C++20 ranges) |
| CMake           | `3.22.1`                | native build                                                                |
| JDK 8           |                         | LWJGL's Java-8 multi-release layer                                          |
| JDK 25          |                         | caciocavallo + frenchpress (`--release 25`)                                 |
| ant, ninja, zip |                         | submodule build scripts                                                     |

Set `ANDROID_HOME`/`ANDROID_NDK_HOME` appropriately.

### 1. Clone with submodules

```bash
git clone --recurse-submodules https://github.com/SKonstruct/SKapsule.git
cd SKapsule
```

### 2. Build the native components

These compile each submodule for `arm64-v8a` and drop outputs into `out/`:

```bash
./scripts/build-gl4es-android.sh
./scripts/build-openal-android.sh
./scripts/build-lwjgl3-android.sh        # JAVA_HOME -> JDK 25 (matches dev box), JAVA8_HOME -> JDK 8
./scripts/build-cacio-android.sh         # CACIO_JAVA_HOME -> JDK 25
./scripts/build-frenchpress-android.sh   # FRENCHPRESS_JAVA_HOME -> JDK 25
```

### 3. Stage assets into the launcher

```bash
./scripts/stage-launcher-assets.sh
```

### 4. Assemble the APK

```bash
cd launcher
JAVA_HOME=/path/to/jdk-25 ./gradlew :app:assembleRelease
```

The output lands in `launcher/app/build/outputs/apk/release/`. Without signing
configured (below) this is `skapsule-vX.Y.Z-unsigned.apk`.

### Signing (optional)

Release signing reads, in order, a gitignored `launcher/keystore.properties` then
environment variables. With neither present, `assembleRelease` still succeeds and
emits an _unsigned_ APK. Provide `storeFile`/`storePassword`/`keyAlias`/`keyPassword`
(props) or `SK_KEYSTORE_FILE`/`SK_KEYSTORE_PASSWORD`/`SK_KEY_ALIAS`/`SK_KEY_PASSWORD`
(env) to produce a signed `skapsule-vX.Y.Z.apk`. See `keystore.properties.template`.

In CI, the keystore is supplied via the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD` repo secrets. The **Release APK** workflow (run manually,
with a version) builds and signs the APK and, only if that succeeds, creates the tag and a
GitHub Release with the APK attached.

---

## How it works

```
LauncherActivity ─ unpacks JRE + runtime, picks server and Web/Steam, launches GameActivity
        │
GameActivity (:game process) ─ starts the FCL JVM, sets up EGL/GLES + input
        │
native (sklauncher.c) ─ boots the JVM, calls into the bootstrap
        │
SkBootstrap ─ runs HeadlessGetdown (validates/patches the game), then invokes
              com.threerings.projectx.client.ProjectXApp in-process
```

The game is a standard desktop Java client, so the port supplies everything that
client expects on a platform Android lacks:

| Component        | What it provides                                          | Source                                                                     |
| ---------------- | --------------------------------------------------------- | -------------------------------------------------------------------------- |
| **JRE 25**       | the Java runtime the game runs on                         | bundled asset                                                              |
| **gl4es**        | translates the game's OpenGL calls to OpenGL ES           | [`gl4es`](https://github.com/SKonstruct/gl4es) submodule                   |
| **openal-soft**  | audio                                                     | [`openal-soft`](https://github.com/SKonstruct/openal-soft) submodule       |
| **LWJGL 3.4.1**  | windowing / GL / input bindings (Android-native build)    | [`lwjgl3`](https://github.com/SKonstruct/lwjgl3) submodule                 |
| **caciocavallo** | headless AWT bridge (the game uses AWT/Swing for some UI) | [`caciocavallo17`](https://github.com/SKonstruct/caciocavallo17) submodule |
| **frenchpress**  | Steam sign-in (QR code / Steam app)                       | [`frenchpress`](https://github.com/SKonstruct/frenchpress) submodule       |
| **skgfx**        | FPS counter, sharpening, Sharp HUD (shared with iOS)      | [`skgfx`](https://github.com/SKonstruct/skgfx) submodule (MIT)             |
| **getdown**      | downloads, verifies, and patches the game                 | bundled `getdown-pro.jar`                                                  |

A small `bootstrap` Java module (`SkBootstrap`, `HeadlessGetdown`) and the launcher's
Kotlin installers wire these together.

### Repository layout

```
launcher/          Android app (Kotlin) — the buildable project
  app/             the Android application module
scripts/           build.sh (everything), build-*-android.sh per native component, stage-launcher-assets.sh
external/bootstrap/       submodule — SkBootstrap / HeadlessGetdown / GLFW shim, staged into the APK
external/gl4es/             submodule — GL → GLES translation
external/openal-soft/       submodule — audio
external/lwjgl3/            submodule — LWJGL Android build
external/caciocavallo17/    submodule — headless AWT
external/frenchpress/       submodule — Steam login
external/skgfx/             submodule — graphics pass (mobile/skgfx.c, skgfx.h), shared with SKapsule-iOS
out/               native build outputs (generated, gitignored)
.github/workflows/ from-source CI + release automation
```

---

## Game Mode

The app declares itself a game (`android:appCategory="game"`), so Android 12+ treats it as
one: the Game Dashboard appears and the system applies its game optimisations.

`res/xml/game_mode_config.xml` opts out of one of them. The platform's downscaling
intervention resizes the backbuffer without telling the app, and Spiral Knights lays its
own UI out in raw framebuffer pixels: below roughly 578 px of surface height the client's
UI stops laying out and character select never appears. The resolution slider already
offers that trade-off, with a floor we know is safe. FPS override stays enabled.

## Graphics

The in-game settings panel (the ⚙ button) has, below **Resolution**: **Show FPS counter** (on by
default), **Sharpening** (0 to 100%, off by default) and **Sharp HUD** (experimental, off by default).
Sharpening and Sharp HUD draw the game off-screen first; Sharp HUD needs Resolution below 100%, and
draws the HUD at the screen's full resolution, so the HUD comes out smaller.

## Editing the touch controls

**Edit Controls** in the options sidebar opens the same editor the ⚙ button opens in game —
drag to reposition, per-control visibility and scale, opacity, resolution, and Reset — without
booting the game first. It opens landscape because the layout is stored relative to the
landscape play area, so what you arrange is what you get.

**Layouts…** in the editor panel keeps named presets, and exports or imports a layout as a `.json` file
(also usable on SKapsule for iOS). Only the arrangement is shared: position, size and visibility of each control,
plus opacity and **Show Buttons**; **Resolution** and **Show Controls** stay yours. Importing never overwrites a
preset: it is saved under a new name, and applying it asks first.

If the game is still running in the background it keeps its own copy of the layout and will
overwrite whatever you change here when it closes, so exit the game first.

In game, a row of buttons sits top-left: **ESC, Settings, Keyboard, Eye**. The eye is a
shortcut for **Show Buttons**, which is off by default and hides every button except ESC,
leaving the joysticks and this row. **Show Controls** turns the touch controls off entirely,
taking the keyboard and eye buttons with them; Settings stays so the editor is still
reachable. All four can be dragged while the editor is open.

## News card

The home screen shows the current in-game events — the same feed the desktop
[KnightLauncher](https://github.com/lucasluqui/KnightLauncher) renders, chosen the same way:
running and upcoming events, or the default event when none are left. With more than one,
the card rotates through them every 8 seconds, starting at a random one; a chip counts down
to the start or end, and turns amber in an event's last two days. Tapping the card opens the
event's page. If there is nothing to show, or the service is unreachable, the card is hidden.

Most default events carry no expiry; those show no countdown, which is normal.

## Crash reporting

Uncaught exceptions and a few detected failures (runtime unpack, mod download/apply/remove,
a failed self-update) are reported to Sentry, except a dropped or unreachable connection (that is your
network, and the app tells you instead), with the device model, Android version, app
build, which process it came from, and the same logcat dump that **Share Logs** exports.
Credentials, tokens and account emails are stripped. Native crashes are not captured: the
SDK's NDK component installs signal handlers, and the bundled JVM in this process needs
those signals for itself.

On by default; **Send crash reports** in the options sidebar turns it off, and the change
takes effect immediately.

## Known issues

- **Character-shadow shader artifact.** Character shadows can render as a filled
  quad. The shader _link_ bug is fixed, but a visual artifact persists from another
  cause. Workaround: set graphics quality to **Low**.
- **APK size.** The bundled runtime makes for a large (~90 MB) APK; it is untuned.

---

## Support

Something wrong? [Open an issue](../../issues/new/choose) and attach the log: **Share Logs** in the options
sidebar bundles the game's output, crash reports and a once-a-minute memory line (Java heap, native heap and
graphics), with credentials and emails removed. For a crash that comes after a long session, share the log
right afterwards, since that is when the memory line shows what grew.

---

## References

These projects were consulted during development. They are not part of the build (the
repo's `refs/` working directory is gitignored), but they're credited here as the
shoulders this port stands on:

- [getdown](https://github.com/threerings/getdown) — Three Rings' application
  installer/patcher/launcher; SKapsule drives it headlessly to update the game.
- [clyde](https://github.com/threerings/clyde) — Three Rings' game tooling/runtime
  library used by the Spiral Knights client.
- [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) /
  [KnightLauncher-Android](https://github.com/SirDank/KnightLauncher-Android) — related
  Android Java-game launchers that informed the JVM-on-Android approach (gl4es, LWJGL,
  cacio bridge).
- The Spiral Knights desktop Linux install layout — reference for the appdir/getdown
  layout SKapsule reproduces on-device.

---

## License

The original code in this repository (the launcher, bootstrap, and build scripts) is
licensed under the **MIT License**.

Bundled and submoduled third-party components retain their own licenses, including
MIT (gl4es, skgfx), BSD (LWJGL, getdown, clyde), LGPL-2.x (OpenAL Soft, dynamically linked as
a separate `.so`), and GPL-2.0 with the Classpath Exception (caciocavallo). The LGPL's
dynamic-linking allowance and caciocavallo's Classpath Exception are what permit the
launcher's own code to be MIT-licensed while distributing those components alongside
it; each component's own `LICENSE`/`COPYING` file holds the authoritative terms.

Spiral Knights and all related game assets are the property of their respective
owners and are **not** covered by this license.

## Translating

The text is in `launcher/app/src/main/res/values/strings.xml` (English). Every language has the same file in
`values-<language>` (`ar`, `de`, `es`, `et`, `fr`, `it`, `ja`, `pl`, `pt-rBR`, `ru`, `zh-rCN`, `zh-rTW`), a copy of the
English text until it is translated. Translate the text between the tags, keep the `name`, the `%1$s`/`%d` placeholders and
apostrophe escaping (`\'`), and leave `translatable="false"` entries alone. `StringsParityTest` (run by
`./gradlew testDebugUnitTest`) fails when a language lacks a key, has an unknown one or loses a placeholder. A new language
also needs a line in `res/xml/locales_config.xml` and one in `LauncherActivity.languages`. The workflow, a prompt for a
model and a review checklist are in the SKradle repository's `docs/translating.md`.
