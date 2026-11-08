# Project status — 4 November 2026

## Overall position

KIRA is the workspace name; the current product is Ultron, an Android voice assistant. The core app, authenticated cloud API, activation-key backend, and license administration website are implemented. This is a working single-phone deployment with substantial automated coverage, not a completed commercial launch.

This review checked current source, infrastructure and CI files, Git history, project tracking documents, the previous PC-setup chat, and local build/test artifacts. It reran the backend tests. It did not authenticate to AWS/Firebase, inspect current GitHub settings or Actions runs, test a phone, or independently verify live deployment state. Deployment statements below are historical records from 3 November, not fresh production checks.

## Exact stopping point

- Current branch: `connector-cleanup`, commit `d322d5d`, 3 November 2026 at 21:42 IST.
- Local `main` and cached `origin/main`: `31a8e25`, merge of PR #34, the license administration website and security work.
- One commit exists beyond local main: removal of the unused `ToolGroup` enum and derivation of GitHub tools. Its remote/PR status was not checked.
- The previous chat, **Check project setup requirements**, restored the PC development environment after reset. The latest audit records Python 3.14.10 and a working backend environment at `X:\tmp\kira-py314`.
- At review start, the only untracked file was `docs/PC_SETUP_AUDIT_2026-11-04.md`. No tracked source changes were pending.

## Implemented

| Area | Evidence and present behavior |
|---|---|
| Voice assistant | Gemini Live clients, microphone/audio pipeline, interruption, prewarming, session controls and foreground service support. Model, voice and language catalogs and settings exist. |
| Android UI | Compose home/voice interface, drawer, connectors, settings, developer controls, skills, knowledge, memory, usage/storage and About/update screens. |
| Phone tools | Device information/controls, alarms/reminders, contacts/calendar/location, calls/SMS, notifications, app navigation and accessibility screen tools. Availability depends on connector settings and Android permissions. |
| Cloud tools | Web search, authenticated web extraction, YouTube features and read-only GitHub integration. GitHub tools are filtered; screen/navigation defaults were disabled in the latest security work. |
| Knowledge and memory | Document storage, PDF preparation, embeddings, ObjectBox index, BM25/vector retrieval, incremental chunk edits, saved personal facts and replacement/recovery logic. |
| Activation keys | Key normalization, hashed keys in DynamoDB, first-activation expiry, revocation, device binding/move cooldown, buyer details, seven-day offline cache and app access gate. |
| License administration | Static `ultronapp/` website and license-admin Lambda; verified admin email allowlist; key creation/list/edit/revoke/extend and move reset. This is an admin interface, not a customer checkout. |
| API foundation | Firebase anonymous identity, API Gateway JWT authorizer, license/admin routes and web/release Lambdas in `infra/api.yaml`. |
| Security work | No Gemini/knowledge keys compiled by the Gradle build; encrypted user key storage; App Check verification on web extraction/releases; persisted SMS throttling; untrusted-content tracking and selected action confirmations. |
| OTA and CI | Publishing script, S3 update delivery, checksum/signature validation, GitHub checks and OTA workflows. Historical notes record successful phone updates. |
| PC setup | JDK 17, Android SDK/tools, AWS CLI, Firebase CLI and Python environment restored. Existing configuration and signing files survived. |

Historical deployment notes record `api.devxjitin.dev` and `ultronapp-admin.web.app` working. On 3 November, web extraction and update checks were exercised on the phone using the new API; legacy public Function URLs were removed and App Check enforcement enabled for web extraction/releases.

## Verification evidence

- Reran backend suite during this review under the project Python 3.14 environment: **63 tests passed**. Tests use fakes/mocks; they do not prove live DynamoDB or Firebase behavior.
- Existing Android XML reports from 4 November: **231 tests in 41 suites, zero failures/errors**.
- Existing lint XML from 4 November: **zero errors, 43 warnings**.
- Existing debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 43,290,528 bytes.
- The setup audit records successful `testDebugUnitTest lintDebug assembleDebug`. This review inspected those results rather than rebuilding Android again.
- At the initial review, CI and Lambda infrastructure targeted Python 3.12. The later project update below aligns the repository with Python 3.14; deployed runtimes require a separate deployment.

## Remaining work and limitations

### Before wider paid distribution

1. **Customer purchase flow:** no customer storefront/checkout or paid-order-to-key issuance integration is present in this repository. `STORE_URL` defaults to blank; a local override or an external store was not verified. Manual key administration is implemented.
2. **Server-side AI entitlement:** the app's access gate checks licenses, but Firebase AI access is not mediated by a backend that checks the license and user quota for each session. There is no implemented session-token or AI-proxy route tying AI consumption to payment/license validity.
3. **Release signing:** OTA still explicitly uses the existing debug signing key. A release signing configuration exists, but the publishing flow has not migrated to it. A migration must account for installed-phone signature continuity.
4. **Publishing safeguards:** checks run on PRs or manual dispatch; OTA publishes on main pushes and does not depend on those check jobs. The workflow has no production environment gate. Current remote branch protections were not inspected; their absence cannot be asserted from local files.
5. **Phone acceptance testing:** repeat activation, expiry, revocation, device move, offline grace, admin access rejection, web extraction, updates and GitHub login after the PC reset. Broaden testing to denied/revoked permissions, reminders after reboot, interrupted downloads and different Android versions.
6. **Action confirmation coverage:** selected outbound actions are guarded after untrusted content. Numeric/coordinate taps are explicitly not guarded; text-tap checks use English action words. This remains a limitation when screen automation is enabled.
7. **License expiry while continuously open:** access refresh occurs at initialization and Activity start. No expiry timer is present in `AccessViewModel`; continuous-use expiry/revocation behavior needs verification and likely a scheduled refresh.

### Cleanup and maintainability

- Decide whether to merge the one cleanup commit beyond local main; verify remote status first.
- Finish the legacy retirement observation period. The recorded earliest cleanup date is **17 November 2026**, contingent on no problems: old `google-workspace-mcp`, `web-extract-mcp` and `ultron-updates` functions/roles/logs. Keep the new API functions and shared extraction code.
- Verify and retire obsolete subscription resources: retained `ultron-subscriptions`, old Razorpay SSM parameters/webhook. Notes identify these as leftovers; their current existence was not checked.
- Update documentation. The root README still claims navigation/screen tools are on by default, contradicting current security work and its own privacy section. It also lists old toolchain versions. October review counts and architecture phase tables describe historical states.
- Longer-term architectural work remains: large ViewModel/device runner, shared tool/permission registries, stronger storage boundaries, server-side usage accounting, richer integration/UI coverage and accessibility/large-font QA.

## Plans that were intentionally superseded

- In-app Razorpay subscriptions and customer Google sign-in were replaced by activation keys. Google sign-in remains for website administrators.
- Firestore remote prompt management, Remote Config/force-update flow and the previous prompt console were removed. The new website manages licenses; it does not restore remote prompt editing.
- The old white-label kiosk/setup-wizard roadmap is not the current implemented product contract.
- Video/screen streaming, full text-chat history and older experimental screens in the activity log should not be counted as current delivered features merely because they once existed.
- The October Astra report's 33/37 completion count is historical and cannot be used as today's project completion percentage.

## Suggested continuation

First verify the cleanup branch against remote main and run a short phone acceptance pass. Then complete the storefront/key issuance flow and server-side AI entitlement before expanding paid distribution. Address signing/publishing safeguards alongside that work. Schedule legacy resource cleanup only after its observation period and a fresh cloud inventory.

## Subsequent implementation — YouTube split, 4 November

At the user's request, YouTube lookup and downloads were separated after the review above. Search/details/transcripts/channel lists now use the new AWS `ultron-api-youtube-search` service at `/v1/youtube/mcp`, implemented with yt-dlp without an API key. The separate `youtube_downloads` connector invokes a private in-process MCP server on Android, keeping NewPipe media extraction, format choice and downloads on the phone. Existing individual tool selections are preserved.

The new independent `ultron-youtube` CloudFormation stack deployed successfully. Live IAM-only AWS tests passed for all four lookup tools; unauthenticated HTTP and missing-App-Check requests were rejected. Backend tests increased to 76, Android tests to 236; lint has zero errors and 43 warnings, and the new debug APK builds. No phone was connected, so this APK has not been installed or tested on-device. See `mcp/youtube-search/README.md` for deployment and verification details.

## Subsequent project update — Python 3.14, 4 November

Aligned `.python-version`, backend CI, all three Lambda templates, both Linux dependency-package builders and current setup documentation with Python 3.14. CI now builds the Lambda ZIPs and imports their packaged Linux dependencies, including native lxml, under Python 3.14. The web-extract package bounds python-dateutil below 3 to remain compatible with the AWS SDK.

All 76 backend tests passed under local Python 3.14.10, local HTML extraction passed, and AWS accepted all three templates in `validate-template`. Both Linux Lambda ZIPs rebuilt successfully and passed ZIP integrity checks. The web package contains Python 3.14 native lxml libraries and python-dateutil 2.9; neither package contains Python 3.12 extensions. The packaged Linux import checks were added to CI and have not been executed on this Windows PC. This is a repository/runtime-target update: the live services were not migrated or redeployed during this request. Earlier live YouTube verification used Python 3.12.
