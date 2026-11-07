# Audit validation

Date: 6 November 2026. Source revision: `f1a0c3171006dfbaeee954ef6098cdaa4df4fe60`.

## Executed

| Check | Result | Evidence |
|---|---|---|
| `python -m unittest discover -s mcp/tests -v` | 66 tests, OK | [backend-tests.txt](backend-tests.txt) |
| `python -m unittest discover -s tests/ci -v` | 13 tests, OK | [ci-tests.txt](ci-tests.txt) |
| `android/gradlew.bat testDebugUnitTest lintDebug --console=plain` from android directory | BUILD SUCCESSFUL, 3m34s; 279 tests, 0 failures/errors/skips; lint 0 errors, 41 warnings | [android-checks.txt](android-checks.txt); generated XML in `android/app/build/test-results/testDebugUnitTest/` |
| `python codex/reproduce_findings.py` | Three probes passed (they demonstrate existing defects) | [reproductions.txt](reproductions.txt), [script](reproduce_findings.py) |

Android counts were summed from generated TEST-*.xml after successful command. Full lint output is preserved in [android-lint.txt](android-lint.txt). Lint warnings are not a clean lint claim. Existing tests cover substantial pure logic, but passing tests did not catch new reviewed interleavings.

B01 invokes actual license Lambda through repository test fake with a revoke inserted between read/write. No real DynamoDB or network. B02/B03 are expression probes mirroring Kotlin predicates with source assertions; they are **not** compiled Kotlin regression tests, phone reproductions, or successful prompt-injection attacks. See technical report for confidence labels.

## Not executed / not claimed

- No physical/emulated phone UI, microphone, lockscreen, background lifecycle or accessibility journey.
- No release/OTA install or production signing migration.
- No live AWS/Firebase/GitHub setting inspection; templates/workflow aren't proof of deployed configuration or branch rules.
- No admin web browser interaction, auth session or real license mutation.
- No real Gemini calls, provider spend, retrieval quality benchmark, latency or battery benchmark.
- No comprehensive dependency advisory scan, secret-history scan or penetration test.
- No exhaustive compiler-based dead-code reachability analysis. Candidates require removal/build proof.
- Publish PowerShell regression script was not executed in this audit; Python CI tests are distinct from that script.

## Follow-up acceptance suite

1. License: concurrent revoke/expiry/transfer, late async refresh after remove/new key, time rollback and offline lease boundary.
2. Tools: high-risk action without confirmation rejected, exact args binding, malicious external instruction, conflicting screen actions and duplicate delivery.
3. Permissions: allow, deny once, permanently deny, activity background and session cancellation during dialog.
4. Reminders: next-day, daily/weekly vs interval, timezone/DST, restart/reboot, cancellation and missed-run policy.
5. Updates: oversized body, timeout, cancellation, hash failure, disk-full and successful signed install preserving data.
6. Admin: retry same creation request, partial-batch failure, cursor pagination, revoked access and audit event integrity.
7. Voice: cold/warm launch, headset route, incoming call, interrupted reply, process recreation and lockscreen.

Only report-related files were intentionally added under `codex/`; Android checks also generate normal ignored build artifacts. Existing application source was not modified.
