# Troubleshooting and connection safety

Use this guide only for the selected authorized device. Do not broaden task permissions to make a failing command work.

## Connection triage

| Symptom | Next step |
|---|---|
| `adb` missing | Explain the dependency; follow the user's installation policy |
| Empty device list | Check cable/data mode, developer options, and user-enabled USB debugging |
| `unauthorized` | User unlocks the phone and reviews the RSA authorization prompt; do not bypass it |
| `no permissions` on Linux | Check the distribution's Android USB/udev rules and required group membership; ask before system changes. Do not run the whole Agent as root |
| `offline` or disconnect | Stop mutations; inspect the same transport, cable/network, and device state |
| Several entries | Ask which device/transport; never default to the first row |
| `device` but no usable screen | Check boot completion, lock screen, foreground profile, and capture capability |
| Command times out after a mutation | Mark outcome unknown; inspect before retrying |

Do not automatically run `adb kill-server`: it disrupts every ADB client using that server. Restart only with explicit authorization and awareness of other tasks. Do not scan networks or pair with discovered phones automatically. If the selected transport changes, verify that the new entry represents the same authorized phone before switching.

## Empty UI tree or ineffective taps

1. Obtain a fresh screenshot if privacy policy and vision capability permit.
2. Check the current app/activity, resolution, rotation, keyboard, and overlays.
3. Prefer a visible, unoccluded target; verify its position against the actual image dimensions.
4. If input commands exit successfully without effect, inspect OEM debugging restrictions rather than repeatedly tapping.
5. If the screen is secure/blank or the Agent cannot view images, ask the user to perform the step. Do not bypass secure-window policy.

WeChat, Flutter, games, and embedded web views can expose incomplete accessibility information. This is screen/version dependent, not a universal claim about an app.

## Xiaomi / Redmi / HyperOS / MIUI

Historical device experience suggests separate developer settings may matter:

- USB debugging permits the basic connection.
- USB debugging (Security settings), where available, may be needed for injected taps/swipes.
- Install via USB, where available, may affect APK installation.

Names, availability, account requirements, and behavior vary by ROM/version. A working Home key with ineffective taps is a diagnostic clue, not proof of a specific setting. Ask the user to inspect the actual device settings and explain the elevated control being granted. Do not enable every developer option indiscriminately.

If an authorized change does not take effect, a user-performed cable reconnect may help. `INSTALL_FAILED_USER_RESTRICTED` can have several causes, including device policy; do not assume an OEM toggle is the only explanation or circumvent enterprise restrictions.

The historical notes discuss enabling preinstalled Google services on one ROM. That procedure is not a general prerequisite for ADB. Do not mass-enable packages or alter Google services for unrelated tasks.

## Wireless debugging: opt in, not automatic setup

ADB grants powerful access to the phone. Prefer USB for initial setup or a trusted, explicitly configured wireless-debugging session. Never expose an ADB listener to the public internet, use router port forwarding for it, or store real pairing codes/addresses in shared documents.

### Paired wireless debugging

Android 11+ phones with supported wireless debugging can pair through a user-visible pairing code. Network/OEM restrictions still apply. The pairing port and connection port may differ and may change between sessions.

```bash
adb pair DEVICE_HOST:PAIRING_PORT
```

The user should enter the pairing code through a private interactive prompt, not paste it into Agent logs. If the tool cannot handle private interactive input, ask the user to run pairing in their terminal.

After pairing, connect to the exact endpoint shown by the device if automatic discovery did not connect it:

```bash
adb connect DEVICE_HOST:CONNECTION_PORT
adb devices -l
```

Then select that exact transport as `ANDROID_SERIAL` for all device commands. Do not assume `5555` is the port for paired wireless debugging. Re-check the current endpoint after restarting or changing networks.

### Legacy TCP mode

Only when explicitly requested, with a trusted restricted network and a recovery route. This mode is different from paired TLS wireless debugging; do not assume its transport is encrypted. A VPN tunnel protects only the route through that tunnel, not other interfaces where the ADB listener may be reachable.

Start from the verified USB transport:

```bash
adb -s "$ANDROID_SERIAL" tcpip 5555
```

Connect only to the approved endpoint:

```bash
adb connect DEVICE_HOST:5555
adb devices -l
```

Verify identity and select the new transport before subsequent actions. Keep physical USB access available as a fallback. Reboots commonly require re-enabling legacy TCP mode; do not promise persistence on every ROM.

When the user wants to stop legacy TCP mode, switch the currently selected device back to USB:

```bash
adb -s "$ANDROID_SERIAL" usb
```

This can immediately end the remote connection. Complete other restoration first and arrange local access. `adb disconnect DEVICE_HOST:5555` disconnects the host client but does not, by itself, turn off the phone's TCP listener. For paired wireless debugging, turn off the corresponding phone setting or forget pairing when the user requests it; do not revoke existing authorizations as routine cleanup.

## VPN and remote recovery

Android generally permits one active VPN service per user/profile. Tailscale and another VPN-based proxy may conflict; this depends on their operating modes and the profile. Starting one may disconnect the other and strand the remote Agent.

Before any authorized network/VPN change:

1. Identify whether the ADB connection depends on the current VPN.
2. Establish a physical USB or user-operated recovery route.
3. Explain the expected disconnection and obtain approval for the specific change.
4. Never force-stop a VPN merely to improve an app's network access without that plan.

No real device endpoint, VPN hostname, serial, or personal account belongs in the published skill.

## Unexpected behavior and escalation

- Text appended twice: stop; verify the field and undo only the task's changes with authorization. Do not blindly clear pre-existing text.
- Send status unclear: inspect outgoing state once the UI settles; never send again just because the command timed out.
- Wrong account/profile: stop before typing or exporting. Resolve the intended target with the user.
- Repeated same-name list entries: use stable identifiers or maintain explicit duplicate uncertainty.
- Budget reached: stop task progress, use the reserved budget for authorized restoration, and report a checkpoint. Do not silently continue the task.
- Restore failed or Android user changed: report the task-owned setting still changed. Do not use guessed defaults or operate on another user/device.

## References

- [ADB documentation](https://developer.android.com/tools/adb)
- [Android developer options](https://developer.android.com/studio/debug/dev-options)
- [Android VPN service overview](https://developer.android.com/develop/connectivity/vpn)
