# CLAUDE.md: Earth idle game

Kotlin Android app in `app/`. The simulation runs on the JVM, so most behaviour is tested
without an emulator. Many tests are parity tests against the original TypeScript engine
(`tools/ts-reference/`); a parity failure means the Kotlin port diverged, not that the
reference is wrong. Developer guide: README "For developers" and `docs/`.

## Model routing for this repo
- Opus: economy/balance changes that touch the simulation core, save compatibility.
- Sonnet: features, UI, tests, build and release scripts.
- Haiku (`explorer`): finding code, summarizing lint or test reports.

## Verification (same as CI)
1. `./gradlew testDebugUnitTest`
2. `./gradlew lintDebug lintRelease`
3. `./gradlew assembleDebug`
4. Dependency changes: `python3 scripts/third-party-notices.py --check`
5. Doc changes: `python3 scripts/check-docs-links.py`
6. UI changes: check on the emulator with the Android QA plugin and attach a screenshot.

Never commit the release keystore or its passwords.
