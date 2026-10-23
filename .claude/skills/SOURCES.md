# Skill sources

Copied from these repositories at the pinned commits. Markdown only: no scripts
or binaries. Each skill keeps its own license file. Update by re-fetching at a newer commit and
reading the diff first.

| Skill | Source | Commit | License |
|---|---|---|---|
| android-cli | github.com/android/skills `devtools/android-cli` | 4ebabf2 | Apache-2.0 |
| android-intent-security | github.com/android/skills `security/android-intent-security` | 4ebabf2 | Apache-2.0 |
| android-permissions-security | github.com/android/skills `security/android-permissions-security` | 4ebabf2 | Apache-2.0 |
| anr-audit-and-fix | github.com/android/skills `performance/anr-audit-and-fix` | 4ebabf2 | Apache-2.0 |
| android-adb-control | github.com/robbin/android-adb-control (SKILL.md, references/, docs/) | 82b4174 | MIT |

`android-adb-control`: `docs/droidclaw-notes.md` (Chinese-only design notes) and the SKILL.md line linking to it were removed; nothing else is edited.

`android-cli` tells the agent to install Google's `android` CLI with `curl … | bash` from
dl.google.com when it is missing; review that before letting an agent do it.
