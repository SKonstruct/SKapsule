## What this changes

<!-- One logical change per PR. If you're tempted to write "and also", it's probably two PRs. -->

## Why

<!-- The problem being solved. Link the issue if there is one: Fixes #123 -->

## How it was verified

<!-- Say what you actually ran. "Should work" is not verification. -->

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] `./gradlew :app:test` succeeds (or: no test-covered code changed)
- [ ] Tested on a device — model and Android version:

<!-- For UI or touch-control changes, a screenshot or short clip saves a round trip. -->

## Checklist

- [ ] Docs updated in the same change (README / CHANGELOG / AGENTS), or nothing needed updating
- [ ] No new third-party dependencies, or they're called out above and justified
- [ ] No secrets, keystores or `keystore.properties`
- [ ] No submodule pointer moved as a side effect
- [ ] No changes under `.github/workflows/`, signing config, release plumbing or
      `launcher/app/src/main/assets/` — or they were agreed beforehand
- [ ] Touch-control behaviour, defaults or layout: checked against
      [SKapsule-iOS](https://github.com/SKonstruct/SKapsule-iOS) so the two ports stay comparable

## Anything reviewers should look at closely

<!-- Tradeoffs you made, things you're unsure about, or code you'd like a second opinion on. -->
