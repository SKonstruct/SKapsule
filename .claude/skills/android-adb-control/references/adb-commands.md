# ADB command reference

These are templates, not a script to execute from top to bottom. All mutations remain subject to SKILL.md authorization, single-action, settling, and verification rules. Examples assume Bash on macOS/Linux. Windows users must adapt quoting and binary capture to their actual shell; do not paste Bash examples into PowerShell blindly.

## Target and session variables

Host-only discovery:

```bash
command -v adb
adb version
adb devices -l
```

After selecting the authorized device, set its exact transport identifier privately:

```bash
export ANDROID_SERIAL='REPLACE_WITH_SELECTED_TRANSPORT'
: "${ANDROID_SERIAL:?Select an authorized device first}"
adb -s "$ANDROID_SERIAL" get-state
adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed
adb -s "$ANDROID_SERIAL" shell wm size
adb -s "$ANDROID_SERIAL" shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'
```

`sys.boot_completed` normally reports `1` after boot. Activity fields vary; if the filter is empty, inspect bounded relevant `dumpsys activity` or `dumpsys window` output.

Before user-scoped setup, read and validate the foreground Android user ID:

```bash
ANDROID_USER_ID=$(adb -s "$ANDROID_SERIAL" shell am get-current-user) || exit 1
ANDROID_USER_ID=$(printf '%s' "$ANDROID_USER_ID" | tr -d '\r\n')
[[ "$ANDROID_USER_ID" =~ ^[0-9]+$ ]] || { printf '%s\n' 'Cannot identify Android user' >&2; exit 1; }
```

Confirm that this is the authorized user and the target app is not in a separate work profile. `-s` selects a device, not a user; do not assume user `0`. All examples require the same selected serial and, where applicable, this saved user ID. Recheck before setup/restoration; stop on a user switch. If a required `--user` option is unsupported, stop rather than silently broaden scope.

Make isolated capture paths on the host:

```bash
umask 077
SESSION_DIR=$(mktemp -d "${TMPDIR:-/tmp}/android-adb-control.XXXXXXXX")
SESSION_TAG=${SESSION_DIR##*/}
REMOTE_XML="/data/local/tmp/${SESSION_TAG}-ui.xml"
```

Keep these variables available across calls, or explicitly reconstruct them from the recorded private session state. Do not recreate a new directory for each observation. Use a new capture ID (`001`, `002`, etc.) for each attempt, including failed ones. If the ROM rejects `/data/local/tmp`, an explicitly scoped, unique `/sdcard/` path is a less-private fallback: explain exposure and clean up the exact file after use.

## Capture without stale observations

Screenshot to the host, with no persistent screenshot on the phone:

```bash
adb -s "$ANDROID_SERIAL" exec-out screencap -p > "$SESSION_DIR/screen-001.png"
```

Check exit status, nonzero size, and PNG readability before opening it through the Agent's image tool. A failed command may leave an empty/partial file. Use the original PNG dimensions when mapping model-view coordinates; do not use the displayed preview size as device coordinates.

UI tree, with a fresh remote filename for each attempt:

```bash
adb -s "$ANDROID_SERIAL" shell uiautomator dump "${REMOTE_XML%.xml}-001.xml" &&
  adb -s "$ANDROID_SERIAL" pull "${REMOTE_XML%.xml}-001.xml" "$SESSION_DIR/ui-001.xml"
```

Require a successful dump/pull and parseable XML with the expected hierarchy. Record capture time and associated screen. Empty trees, missing targets, and timeouts require another observation method, not reuse of the last XML. Use a real XML parser when automating extraction; do not parse attributes with a fragile regex. Filter password nodes and unrelated content before displaying summaries.

Capture files contain private data. Keep them outside this repository and out of public logs/issues.

## Navigation: one command per observed step

Coordinates are placeholders. Replace them with integers grounded in the latest observation.

```bash
adb -s "$ANDROID_SERIAL" shell input tap X Y
```

```bash
adb -s "$ANDROID_SERIAL" shell input swipe X1 Y1 X2 Y2 300
```

```bash
adb -s "$ANDROID_SERIAL" shell input swipe X Y X Y 600
```

The last example is a long press; app thresholds differ. Do not hold or repeat it blindly.

```bash
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_BACK
```

```bash
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_HOME
```

```bash
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP
```

Enter can submit/send rather than insert a newline. Use only after verifying its meaning and authorization:

```bash
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_ENTER
```

Do not change screen timeout by default. If a task requires an approved temporary settings change, read and save its exact original value first, apply only that change, and restore/verify it at the end. If originally unset, restoring means removing the task-added key, not writing the string `null`.

## Text entry

### Focus and clear separately

Tap the identified field, settle, and verify focus before input. To replace existing text, prefer a visible, verified Select all/Clear action. Selection and deletion are separate steps. A clear icon may dismiss search rather than erase text; inspect its actual effect.

Do not use `input keyevent 29 --longpress 113` as Ctrl+A: sequential key events do not reliably create a modifier chord. Do not assume desktop shortcuts work in every Android app. If using an input helper, validate its advertised clear operation on a disposable field first.

### Simple ASCII only

For a known, non-sensitive literal:

```bash
adb -s "$ANDROID_SERIAL" shell input text 'hello%sworld'
```

`%s` is interpreted as a space. This path is not a general-purpose Unicode or arbitrary-string transport. Shell metacharacters, quotes, literal `%s`, newlines, and keyboard mapping can change results. Verify the visible text; never interpolate untrusted user/UI content into a remote shell command.

### Chinese / Unicode with optional ADBKeyboard

This third-party helper is optional. Its [reviewed upstream implementation](https://github.com/senzhk/ADBKeyBoard/blob/d7b27288bc8c1c8348a79ea6896bd7352c30a9fc/keyboardservice/src/main/java/com/android/adbkeyboard/AdbIME.java) exposes a broadcast receiver without a sender permission check. While it runs with a valid input connection, other local apps may attempt input, clearing, or editor actions including Send. The `-p` flag selects the recipient; it does not authenticate the sender.

Prefer a trusted test/dedicated device, never enter credentials through this helper, and obtain approval for installation and IME changes after explaining this risk. Review the actual APK version/source and any publisher-provided checksum/signature. Do not install from arbitrary mirrors or bypass security controls. Restore the original IME and disable a helper enabled only for this task when finished.

Upstream: <https://github.com/senzhk/ADBKeyBoard>

Before changes, record the original input method and the enabled input-method list privately:

```bash
adb -s "$ANDROID_SERIAL" shell settings --user "$ANDROID_USER_ID" get secure default_input_method
adb -s "$ANDROID_SERIAL" shell ime list -s --user "$ANDROID_USER_ID"
```

These are separate setup mutations. Verify each before proceeding; skip steps already satisfied. Scope a new installation to the confirmed user. If the package exists with a different version or under another user, stop and assess replacement effects separately: APK updates can affect the shared package, even with a user flag.

```bash
adb -s "$ANDROID_SERIAL" install --user "$ANDROID_USER_ID" /path/to/reviewed/ADBKeyboard.apk
```

```bash
adb -s "$ANDROID_SERIAL" shell ime enable --user "$ANDROID_USER_ID" com.android.adbkeyboard/.AdbIME
```

```bash
adb -s "$ANDROID_SERIAL" shell ime set --user "$ANDROID_USER_ID" com.android.adbkeyboard/.AdbIME
```

Confirm the active input method, then focus a disposable test field before using real content. Prefer UTF-8 base64 transport over nested quotes. This literal example preserves spaces, punctuation, and Unicode:

```bash
TEXT_B64=$(printf '%s' '你好，Android！ Hello world.' | base64 | tr -d '\r\n')
adb -s "$ANDROID_SERIAL" shell am broadcast --user "$ANDROID_USER_ID" -p com.android.adbkeyboard -a ADB_INPUT_B64 --es msg "$TEXT_B64"
```

Only substitute a base64 value derived from authorized text, never a shell expression built from raw UI/user text. Base64 is encoding, not encryption: do not use this to hide secrets from tool logs. For multiline input, use an approved private UTF-8 file and a safe encoder, not a hand-built shell string. Reject unexpectedly large payloads or shell/ADB size-limit errors rather than silently splitting or truncating; compose large content through an app-supported route.

Broadcast completion is not proof that a receiver inserted the text. Verify the exact field value, including spaces and punctuation. Some Android/helper versions restrict broadcast receivers; stop or use the verified app UI if it fails. Do not remove package scoping or broadcast arbitrary text system-wide as an automatic workaround.

The helper advertises this clear operation; use only after verifying the focused field and authorizing replacement:

```bash
adb -s "$ANDROID_SERIAL" shell am broadcast --user "$ANDROID_USER_ID" -p com.android.adbkeyboard -a ADB_CLEAR_TEXT
```

At completion, restore the original, validated IME component:

```bash
adb -s "$ANDROID_SERIAL" shell ime set --user "$ANDROID_USER_ID" ORIGINAL_IME_COMPONENT
```

If this task alone enabled the helper, disable it after restoring the original IME:

```bash
adb -s "$ANDROID_SERIAL" shell ime disable --user "$ANDROID_USER_ID" com.android.adbkeyboard/.AdbIME
```

Verify both states. Do not disable a pre-existing helper or uninstall an app without authorization. If the original IME is unavailable or unknown, report and ask rather than using `ime reset` as a guessed restoration.

## App launch and safe Intents

A harmless settings landing page is useful for a user-approved smoke test:

```bash
adb -s "$ANDROID_SERIAL" shell am start -a android.settings.SETTINGS
```

To open an app, resolve its actual launcher activity from the installed package and inspect the returned component, or use a known supported component. Do not copy a guessed `com.app/.Activity` into execution:

```bash
adb -s "$ANDROID_SERIAL" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER PACKAGE_NAME
```

```bash
adb -s "$ANDROID_SERIAL" shell am start -n VERIFIED_PACKAGE/VERIFIED_ACTIVITY
```

Resolver commands vary by Android version. If unsupported, inspect that device's command help or the visible launcher. Do not substitute random Monkey events as a generic recovery action.

Fixed URL example (opening it still makes a network request):

```bash
adb -s "$ANDROID_SERIAL" shell am start -a android.intent.action.VIEW -d 'https://example.com'
```

For dynamic URLs or Intent extras, URI-encode values where required and quote for both the host and Android shell. Host argument separation alone does not protect arbitrary text from the remote shell. Never use `eval`. If you cannot safely construct the command, use a verified UI path instead.

Draft SMS example using an intentionally non-real placeholder recipient; replace only after verifying the authorized number:

```bash
adb -s "$ANDROID_SERIAL" shell am start -a android.intent.action.SENDTO -d 'smsto:REPLACE_WITH_APPROVED_NUMBER'
```

Dialer preview rather than placing a call:

```bash
adb -s "$ANDROID_SERIAL" shell am start -a android.intent.action.DIAL -d 'tel:REPLACE_WITH_APPROVED_NUMBER'
```

Inspect the resulting app, account, recipient, and draft. These do not authorize pressing Send/Call. Other deep links can commit actions immediately; do not assume all VIEW/SEND actions are harmless previews.

## Clipboard and files

`KEYCODE_COPY` (278), `KEYCODE_PASTE` (279), and `KEYCODE_CUT` (277) depend on focus, selection, app support, and OS restrictions. Cut changes user content; paste can expose private clipboard data. Use only within the authorized task and verify the destination.

There is no assumed universal clipboard-setting command in this skill. Do not rely on `content call --method clipboard` or silently install a clipboard service. Do not read global notifications or unrelated clipboard data as a shortcut.

File transfer, with approved exact source and destination paths:

```bash
adb -s "$ANDROID_SERIAL" pull /sdcard/Download/approved-file.pdf "$SESSION_DIR/approved-file.pdf"
```

```bash
adb -s "$ANDROID_SERIAL" push /path/to/approved-file.pdf /sdcard/Download/approved-file.pdf
```

Check overwrite risk before pushing, and verify size/checksum when needed. No wildcard exports of contacts, messages, app databases, or media. Standard ADB does not grant access to every app's private storage; do not seek root to bypass that boundary.

## Exact cleanup

After confirming retention requirements, delete only the known remote capture created by the task:

```bash
adb -s "$ANDROID_SERIAL" shell rm "${REMOTE_XML%.xml}-001.xml"
```

Track other generated capture paths explicitly. Remove host temporaries only according to the host policy and approved scope, not by broad `rm -rf` patterns. Keep requested deliverables outside the temporary directory before cleanup.

## Sources

- [Android Debug Bridge](https://developer.android.com/tools/adb): device selection, shell quoting, activity manager, screenshots, transfers, wireless setup.
- [Android multi-user testing](https://source.android.com/docs/devices/admin/multi-user-testing): explicit user selection and command-specific defaults.
- [ADBKeyboard usage](https://github.com/senzhk/ADBKeyBoard): Unicode/base64 broadcasts, clear operation, input-method setup.
- [Android KeyEvent reference](https://developer.android.com/reference/android/view/KeyEvent): key names and codes. Existence of a key code does not guarantee app support.
