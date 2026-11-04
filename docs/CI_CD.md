# CI/CD behavior

## Validation

`Checks` runs on every PR and can be called by the release workflow. A complete Git diff selects the Android, backend and browser-module jobs. Docs-only PRs still run the inexpensive workflow-logic tests and an always-running `Required checks` gate, so required status checks cannot remain pending because a workflow was skipped.

Android validation runs unit tests and lint in one Gradle invocation with `-PappVersionCode=1`, task-output caching and a timing profile. The stable validation version avoids minute-by-minute BuildConfig invalidation. This version is for validation only and is never published. Configuration caching is not enabled until plugin compatibility is established.

Backend validation uses Python 3.14, caches pip downloads, runs tests, builds both Linux packages and imports their actual dependencies. Browser modules receive syntax checks. Changes to CI selection or validation logic conservatively validate all components.

## Releases

`Publish OTA` runs on main pushes affecting Android, the publish script, the publish workflow or local actions. Backend, website, Python-version, documentation and checks-only changes do not automatically release an APK. Manual publishing is available on main.

1. Preflight rejects non-main releases and skips obsolete commits before installing build tools.
2. The same commit runs the reusable `Checks` workflow. A failure prevents the publishing job from starting; signing material and AWS credentials are only restored after validation.
3. The build step runs `publish-update.ps1 -Mode Build`. It builds the shrunk OTA APK, records its exact source commit/hash/size/version in `ota-manifest.json`, and performs no uploads.
4. The upload step runs `-Mode Publish`. It checks the build manifest, requires a strictly newer version than live, rechecks source freshness, uploads the APK and only then updates `latest.json`.
5. Gradle HTML timing profiles are retained as workflow artifacts for seven days. Build and upload are separate timed steps.

Local one-command publishing remains `./scripts/publish-update.ps1 -Notes "Changes"`, defaulting to `BuildAndPublish`. It requires an existing readable `latest.json`; authentication or network failures stop publication. The existing signing key and three-APK retention policy are preserved.

## Repository protection

Protect main with a PR requirement, required `Required checks` status, up-to-date branches, resolved conversations, administrator enforcement, and force pushes/deletions disabled. No approval count is required for a sole-maintainer repository. This repository setting is independent of YAML; inspect the actual GitHub API result before claiming it is active.

## Scope and timing

These workflows publish Android OTA updates. They validate backend/website changes but do not deploy Lambda, CloudFormation or Firebase Hosting. Python 3.14 runtime deployment remains separate.

Tests now gate main releases, so total release time is not guaranteed to decrease immediately. Dependency and task-output caches reduce repeat work; selective jobs avoid unrelated work. Use the generated profiles and real GitHub run durations to measure improvements rather than estimating a promised speedup.
