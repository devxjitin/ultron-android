# CI/CD and deployed artifact review — 4 November 2026

## Verdict

The current main-branch OTA pipeline is operational and recent deployments are consistent with their artifacts. It is not fully protected or optimized. The dominant measured delay is the Android build, not SDK setup, authentication, S3 uploading or Python. No workflow or production setting was changed during this audit.

Remote main is `31a8e252fb57cac148a245eae51eda5e4581ccfb`. Today's YouTube split and Python 3.14 configuration edits remain local and are not part of that commit or its CI results.

## Measured GitHub runs

| Run | Result | Workflow elapsed | Publish job | Build-and-publish step |
|---|---|---|---|---|
| [41671199905](https://github.com/devxjitin/ultron-android/actions/runs/41671199905), PR #34 merge | Success | 10m 44s | 10m 39s | 9m 37s |
| [41587317206](https://github.com/devxjitin/ultron-android/actions/runs/41587317206), PR #33 merge | Success | 10m 39s | 10m 37s | 9m 33s |
| [41583423762](https://github.com/devxjitin/ultron-android/actions/runs/41583423762), manual publish | Success | 13m | Not separately analyzed | Not separately analyzed |
| [41583219256](https://github.com/devxjitin/ultron-android/actions/runs/41583219256), PR #32 merge | Failed | 1m 11s | 1m 8s | Failed after 5s; checkout was behind origin/main |

Of the most recent 12 main-branch runs, 11 succeeded and one was refused by the stale-checkout guard. Successful workflow elapsed times ranged from 8m 58s to 17m 20s, averaging 11m 35s. This small historical sample is not an availability guarantee.

Latest publish-job breakdown:

- Checkout: 2s.
- Java setup: under 1s.
- Android SDK setup: 17s.
- Gradle setup/cache restore: 15s.
- Configuration and AWS identity: under 1s each.
- Build and publish: 577s. Logs show the build starting at 16:09:51 UTC and uploading at 16:19:19 UTC: approximately 568s building; publication completes about 5s after upload starts.
- Gradle cache cleanup/save: 22s.

The latest successful PR check [41670128680](https://github.com/devxjitin/ultron-android/actions/runs/41670128680) took 5m 11s overall. Its Android job took 307s: unit tests/build 128s, lint 134s, SDK 12s and Gradle setup 19s. The Python job took only 12s overall; its test step completed in under one second. The Python 3.14 packaging/import checks added locally have not run on GitHub yet.

## Deployed artifacts checked directly

- S3 `latest.json` points to version `29895370`, version name `1.0.0`, from the PR #34 merge. APK size: 11,158,299 bytes.
- Downloaded the private S3 APK and independently verified its SHA-256 equals the manifest: `49910c088252e03297947483b0b0b31d7727309b1b5fd7a7bf4d776398b3787c`.
- Android `apksigner verify --print-certs` succeeded. The signer is the existing Android Debug certificate; this agrees with the current OTA build configuration.
- Downloaded ZIPs for `ultron-api-web-extract`, `ultron-api-releases`, `ultron-api-license`, `ultron-api-license-admin` and `ultron-api-youtube-search`. Each ZIP's SHA-256 matched AWS's `CodeSha256`, and each `lambda_function.py` matched the workspace after normalizing line endings.
- All five deployed functions still use `python3.12`. The web-extract ZIP contains ten CPython 3.12 native libraries. Local rebuilt Python 3.14 ZIPs have not replaced them.
- The earlier YouTube live tests demonstrated all four lookup tools; the gateway and missing-App-Check checks rejected unauthenticated calls. This audit did not repeat an end-to-end phone walkthrough or exercise every backend API.

## Findings

1. **Publishing is not gated by checks.** Remote `checks.yml` runs for PRs/manual dispatch; remote `publish-ota.yml` independently runs on main pushes. The publish job does not run tests/lint or depend on their success. The GitHub branch response reports `protected: false` for main. A direct main push can therefore publish without the validation workflow running.
2. **Dependency caching works, task-output caching is not enabled.** Recent logs show successful wrapper/dependency/transform cache restores. `android/gradle.properties` does not set `org.gradle.caching=true`, and the actual `assembleOta` command does not pass `--build-cache`. Gradle disables task-output caching by default. This is different from the GitHub action restoring downloaded dependencies. [Gradle build-cache documentation](https://docs.gradle.org/current/userguide/build_cache.html), [setup-gradle caching](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md).
3. **The build is not profiled.** `publish-update.ps1` invokes `assembleOta` with `-q`. The OTA variant enables code minification and resource shrinking. The combined compile/shrink/package interval is measured, but current logs do not establish exactly how much is Kotlin, ObjectBox/KAPT or R8. Disabling shrinking just to shorten CI would change APK size and runtime behavior.
4. **Checks run unnecessary work.** Both Android and Python jobs run on every PR, regardless of which subsystem changed. The local Python 3.14 update additionally builds both ZIPs on each PR; without job-level change detection and pip caching this adds avoidable work for Android-only edits.
5. **OTA triggers are broader than app inputs.** The ignore list excludes docs/infra/mcp but not `ultronapp/**`, `.python-version`, `.gitignore` or changes to the checks-only workflow. Those changes can trigger a full Android publish. A positive app-input path list is clearer.
6. **No automatic Lambda or website deployment.** The two checked-in workflows validate code and publish APKs. They do not deploy CloudFormation/Lambda or Firebase Hosting. Merging a runtime template change cannot itself upgrade live Python. Those components currently depend on manual deployment.
7. **Version codes limit build reuse.** The default Gradle version code changes with the minute. Even repeated PR checks on unchanged source can get different generated BuildConfig inputs. A fixed validation version code is appropriate for tests; published builds still require unique, monotonic codes.
8. **Queued obsolete runs can fail safely.** Publishing is serialized with `cancel-in-progress: false`; a newer main commit can make an older queued checkout fail the guard. That explains the sampled failure; it was not a compiler failure. Detect obsolete work before the expensive SDK/build setup, while keeping the final guard.
9. **OTA signing still uses the debug key.** The downloaded artifact confirms the configuration. A release-key migration requires a deliberate installed-phone update/reinstallation plan; changing the key casually breaks update continuity.

## Recommended order

1. Enable Gradle task-output caching; combine PR unit tests and lint into one Gradle invocation with a fixed test version code. Keep shrinking enabled for OTA. Add a Gradle profile for the next actual publish before assigning time to individual compiler/shrinker tasks.
2. Add per-job change detection so Android-only, backend-only and website-only PRs run the relevant work. Keep an always-running final check if branch protection requires a fixed check name; top-level path filtering can leave required checks pending. [GitHub workflow filtering guidance](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow).
3. Restrict automatic APK publishing to app/build/release inputs; retain manual publishing. Add early stale-run detection and separate build/upload steps for visible timings.
4. Require successful checks and PR review on main, then connect publishing to validated code. Add a production environment gate if manual release approval is desired.
5. Add separately scoped backend and website deploy workflows with smoke checks and rollback plans. For Python 3.14, deploy rebuilt native packages and the runtime change together. Do not deploy the retired legacy stack over the current resources.

A shorter runtime is plausible after these changes, but no speedup has been benchmarked yet. Do not promise a specific reduction from this audit alone.
