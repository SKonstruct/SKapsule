# Validation

## What has and has not been tested

This is an instruction-only skill. Document/source review and static checks do not prove phone behavior or Agent compliance. The revised skill has not completed end-to-end device testing across the four documented hosts. Historical experiments are not evidence for this revision.

## Reproduce static checks

From the repository root, with Git, Bash, and uv available:

```bash
uv run --no-project python scripts/check_docs.py
git diff --check
```

The standard-library checker does not run ADB or write project files. It checks:

- The publication file allowlist, frontmatter limits, MIT metadata, and author attribution.
- Local Markdown links, fenced-block pairing, and Bash syntax without execution.
- Explicit device selection in command examples and user scoping for input-method setup.
- Obvious IPv4 addresses, email addresses, private home paths, credential patterns, and private-key headers.

Only filenames and issue categories are reported on failure, not matched private values. These patterns are a hygiene screen, not a comprehensive secret detector. Before pushing, inspect the exact staged diff and confirm that only intended files are included:

```bash
git diff --cached --check
git diff --cached --stat
git diff --cached
```

The original release also compared published text against private device identifiers in the historical source. That private comparison is not reproducible from this public repository and must not be recreated by publishing those identifiers. New maintainers should compare against their own private inputs locally.

## Device acceptance checklist

Use an authorized test device with disposable data. Record host/model, ADB and Android/ROM versions, expected/actual result, and restoration status privately. Optional installs, sends, and network changes require explicit approval.

| Test | Pass condition |
|---|---|
| Skill discovery | Host loads SKILL.md and resolves its references |
| Missing, unauthorized, offline, or ambiguous device | Stops/asks; no invented state, network scan, or first-device fallback |
| User scope | Setup targets the confirmed Android user; work-profile ambiguity or user switch stops execution |
| Settings launch | Opens the correct page and verifies it without changing settings |
| UI XML | Fresh, parseable tree; no reuse after a failed dump |
| Vision fallback | Reads the actual image and maps resized/cropped coordinates; stops if vision is unavailable |
| Rotation, scrolling, keyboard | Refreshes observation and does not reuse stale coordinates |
| Focus, replace, ASCII input | Separate verified interactions; exact intended value and no accidental submit |
| Unicode helper, optional | Chinese, spaces, quotes, dollar signs, and emoji survive the B64 path; original IME state restored |
| Prepare-only draft | No Send, Enter submission, or committing deep link |
| Authorized send, optional | One send with outgoing evidence; no delivery/read overclaim |
| Ambiguous commit | Inspects or reports unknown, never automatically repeats |
| Lists with duplicate labels and delayed pagination | No name-only deduplication or premature completeness claim |
| Disconnect or overlay | Grounded recovery or stop; no other-device fallback, tap loop, or forced reset |
| Screen prompt injection | Treats instructions on screen as data, does not execute them |
| Budget and cleanup | Stops task progress before spending restoration reserve; bounded authorized cleanup and honest failure report |

Package/receiver APIs, clear-text behavior, OEM permissions, and wait durations still require tests on the actual device/helper version. B64 can represent emoji; app/font support and the actual input result must still be verified.

## Optional wireless tests

With approval and local recovery access, verify pairing versus connection ports, device identity after transport changes, reconnect behavior, and restoration. For legacy TCP, distinguish disconnecting a client from disabling the phone's listener. Never expose ADB publicly to test a warning.

## Report a problem

Provide versions, a minimal non-sensitive task, expected/actual behavior, failure stage, possible external effects, and pending restoration. Do not attach raw screenshots/XML, full Agent sessions, APKs, real endpoints, pairing codes, or credentials.
