# Ultron project review (GPT 6 Astra) — what is done and what is left

Review date: 21 October 2026. Reviewed checkout: `bf47ffc`. This file keeps the review's findings in one place and
tracks what has been fixed since. Fixes live on branch `claude/project-structure-correction-800912`; each round is one commit.

**Scope of the original review:** a source and local-test audit of the Android app, the Python MCP services, the
infrastructure template and the release script. It was not a production test: no live Google/GitHub mutations, AWS
deployment, model requests, microphone sessions or device walkthroughs were run. P1 = fix before wider distribution,
P2 = meaningful reliability or feature defect, P3 = cleanup.

**Original baseline:** 134 Android unit tests passed (19 suites). Lint failed with 7 errors and 25 warnings.

## Summary

| | Count |
|---|---|
| Findings in the review | 37 |
| Fixed (done) | 33 |
| Partly fixed (a real piece is left) | 4 |
| Not started | 0 |
| All P1 findings (F01–F09, F31) | fixed in code; only F08 (durable downloads) has a follow-up piece left |

Current checks: Kotlin compiles, `lintDebug` reports 0 errors, the new Kotlin tests (DOCX, address ranges, memory
replace, schema, SSE, chat endings and cancel) and the 12 Python Lambda tests pass. The full Android unit suite has not
been re-run after the changes, and nothing has been tried on a device or deployed.

## Rounds and commits

| Round | Commit | Findings |
|---|---|---|
| 1 | `22e9766` | F33 (JDK path), F01, F31, F02 |
| 2 | `2d01c3b` | F09, F05, F06 (first part) |
| 3 | `67507ed` | F04, F03 |
| 4 | `3ca7e9d` | F07, F08 (first part) |
| 5 | `dbf0b7a` | F10 (delay), F27, F26, F25, F22 (SSE), F17 (LRU) |
| 6 | `4e54c5d` | F34, F29, F37, F30 |
| 7 | `18348ce` | F21, F28, F24, F23 |
| 8 | `98de44d` | F12, F11, F18, F19, F20, F35 |
| 9 | `52728b3` | F32, F13, F14, F15 |
| 10 | `26e86b4` | F16, F36 (reminder tools), F22 (cancel), lint errors, README |
| 11 | `c0bc03f` | tests; fixed a cancelled call surfacing as an IOException |
| 12 | `9d5b6cc` | F33 (release id), F32 (usage scope), F19, F35 (Repair, photos), F02 check |
| 13 | `eddfc23` | F09 (recovery/export), F05 (forget, restore), F17 (disclosure, export), F06 (streaming parser), F18 (drafts) |
| 14 | `9041aa4` | F21, F23, F28 (partly), F24, F22 (protocol tests) |
| 15 | `ccdfc81` | F13 (purposes, budget, opt-in titles), F14 (reply cap, older-turn digest), F15 (opt-in persona, dismissible notice) |
| 16 | `234d975` | F10, F11, F08 (foreground service, resume), F30 (leases) |
| 17 | `241b513` | Lint warnings that need no upgrade |

## Findings

Status: **Done** = fixed in code, **Partial** = fixed in part, the "Left" column says what remains.

| ID | P | Finding | Status | Left |
|---|---|---|---|---|
| F01 | P1 | New Function URLs lacked `lambda:InvokeFunction` permission | Done (R1) | Not deployed; `InvokedViaFunctionUrl` not validated with CloudFormation |
| F02 | P1 | Reminders failed unless an unrelated connector was on | Done (R1, R12) | Device test on a fresh install plus reboot |
| F03 | P1 | Cancelling chat did not cancel the HTTP request | Done (R3, R11) | — |
| F04 | P1 | A cancelled reply could unlock a newer chat request | Done (R3) | No ViewModel-level test |
| F05 | P1 | Memory replace could delete unrelated facts | Done (R2, R11, R13) | Notes are still matched by words, not IDs shown to the model; ambiguous matches delete nothing and removed notes can be restored from the Memory page |
| F06 | P1 | DOCX size limit did not bound decompressed content | Done (R2, R11, R13) | — (XML is scanned as a stream; unreadable and too-large files say so) |
| F07 | P1 | Link preview address check separate from the connection's DNS | Done (R4, R11) | — |
| F08 | P1 | Concurrent YouTube downloads shared temp files | Done (R4, R16) | A foreground service keeps the process alive while downloads run; a dropped connection resumes from the saved bytes (3 tries). A download is not restarted after the app process is killed; foreground service not tried on a device |
| F09 | P1 | Corrupt chat metadata could delete attachments | Done (R2, R13) | — (Storage page can save or delete the set-aside chats; cleanup resumes afterwards) |
| F10 | P2 | Artificial text pacing and repeated Markdown parsing | Done (R5, R16) | — (finished paragraphs are parsed once and drawn per block; tested equal to a full parse at every length) |
| F11 | P2 | Chat search scanned full history on the UI thread | Done (R8, R16) | — (index built once per chat list, scope chips, result opens at the matching exchange) |
| F12 | P2 | Voice tools blocked the main thread | Done (R8) | — |
| F13 | P2 | Token accounting missed auxiliary model work | Done (R9, R15) | Usage is kept per day and per purpose (chat, names, instructions, PDF), not per single request; AI chat names are now off by default; optional daily token limit |
| F14 | P2 | Context limited by message count, not tokens | Partial (R9, R15) | Reply cap (16K tokens) and a short digest of older messages added; choosing tool groups per turn and model-written summaries are left |
| F15 | P2 | Knowledge edits overwrote the manual voice prompt and blocked the app | Done (R9, R15) | — (redraft is opt-in on the Usage page; the notice can be dismissed) |
| F16 | P2 | Editing or trimming turns leaked attachment files | Done (R10) | — |
| F17 | P2 | Saved conversations silently evicted | Partial (R5, R13) | Limit is now disclosed and chats can be exported as text; moving to a database is left |
| F18 | P2 | Composer draft cleared on rejection, not scoped to a chat | Done (R8, R13) | Typed text survives page changes; attached files do not survive the app process being killed |
| F19 | P2 | Some destinations bypassed the unsaved-changes guard | Done (R8, R12) | — |
| F20 | P2 | Preview failures looked like endless loading; image decode unbounded | Done (R8) | — |
| F21 | P2 | Expired Google tokens stayed cached | Done (R7, R14) | Server now sends an error kind; app keeps the old message check for a Lambda not yet redeployed |
| F22 | P2 | MCP SSE waited for the whole connection to close | Done (R5, R10, R11, R14) | — (client echoes the negotiated protocol version; tested with older/newer versions, JSON and SSE, session loss) |
| F23 | P2 | MCP schema conversion changed argument types | Done (R7, R11, R14) | Simplified tools say so to the model; Azure gets the original JSON Schema. Gemini still gets the reduced dialect |
| F24 | P2 | Google list/read tools returned incomplete data | Done (R7, R11, R14) | — (Sheets in row windows, Slides by slide range incl. grouped shapes, Drive covers shared drives) |
| F25 | P2 | Docs reads ignored all but the first tab | Done (R5, R11) | — |
| F26 | P2 | Gmail reply draft lacked threading headers | Done (R5, R11) | — |
| F27 | P2 | MCP handlers crashed on valid JSON that is not an RPC object | Done (R5, R11) | — |
| F28 | P2 | Timeout budgets did not fit multi-step operations | Partial (R7, R14) | Create event/file are looked up after a timeout and Gmail search is one batch call; other writes only say the outcome is unknown. Batch format and 30 s infra timeout not checked against live Gmail/AWS |
| F29 | P2 | Notes not atomic or locked across store instances | Done (R6) | Concurrency test needs an Android context |
| F30 | P2 | Clear cache could remove active downloads and previews | Done (R6, R16) | Downloads and open previews hold leases; the update folder is still guarded by the running-download check and exports by a 10-minute rule |
| F31 | P1 | Calling an ambiguous contact picked the first match | Done (R1) | — |
| F32 | P2 | Voice errors undercounted session time | Done (R9, R12) | — |
| F33 | P2 | Release script JDK path broken; minute-based release ids | Done (R1, R12) | Script not run end to end |
| F34 | P2 | Streams saved as complete without a successful ending | Done (R6, R11) | — |
| F35 | P2 | Revoked permissions hid connectors | Done (R8, R12) | — |
| F36 | P3 | General automation code unreachable | Partial (R10, R12) | List/cancel reminder tools added; rule engine kept for old saved rules, decision pending |
| F37 | P2 | Retrieval could mix new vectors with old chunk text | Done (R6) | — |

## Android lint

| Diagnostic | Status |
|---|---|
| 2 × Bluetooth `MissingPermission` | Done — permission is checked before the adapter is read |
| 2 × `NewApi` navigation-bar attributes | Done — moved to `values-v27` and `values-night-v27` |
| 2 × `ProtectedPermissions` (WRITE_SETTINGS, PACKAGE_USAGE_STATS) | Done — documented and suppressed; the permissions and their Settings flows stay |
| Optional telephony hardware | Done — `required="false"` |
| `SelectedPhotoAccess` | Done — Android 14 partial photo access handled |
| Warnings: backup/transfer rules, unused colours and round icon, monochrome icon, obsolete icon folder, Modifier order, autoboxing, notification constant, free-space note | Done (R17) |
| Warnings: 11 dependency versions, x86_64 ABI for ChromeOS | Left — need your approval (version upgrades change the build; x86_64 grows the APK) |
| AGP 8.5.2 tested only up to compileSdk 34, project uses 35 | Left — upgrade as one tested unit |

## Review sections not covered by findings

| Area | Status |
|---|---|
| Architecture changes (chat controller, voice in a service, transactional database, one knowledge store, tool registry, parallel reads, request ledger, shared RPC transport) | Left — treated as a separate project |
| Import improvements (batched embeddings, dedupe by content hash, checkpoints), offline BM25 fallback, query cache, source links | Left |
| Shared billable API keys inside the APK; release signing channel documentation | Left |
| Feature-by-feature UX suggestions (Stop/Retry, reconnect state, reminder list UI, usage labels, TalkBack and font-scale checks) | Left |
| Dead code and docs | `ToolGroup.WEB` is in use (internet speed test), so nothing to remove. Web-extract Lambda decision and rules-engine decision (F36) left for you |
| Regression tests | Partly done in R11: DOCX cap, address ranges, memory replace, schema, SSE, chat endings and cancel, Lambda handlers. Still missing: corrupt-history quarantine, concurrent notes, Compose draft/navigation tests |
| Device matrix (API 26/29/33/35+, denied permissions, offline, background, large history, rebooted reminders, OTA signing) | Left — needs a phone |
| Staging deploy of both Lambdas and `infra/ultron.yaml` | Left — needs AWS access |

## Release gates from the review

| Gate | State |
|---|---|
| Zero known P1 findings | Code fixed; F05, F06, F08, F09 follow-ups left |
| Cancellation reliably ends blocked requests | Done and tested against a silent server |
| No stale request can change another chat | Done in code, no ViewModel test |
| No automatic delete after a corrupt read | Done in code, no test (needs an Android context) |
| Reminder delivery works with only its own connector on | Done in code, not tried on a device |
| Large history does not block input | Search moved off the main thread; not measured |
| Usage totals explain every app-initiated request | Partly — title, persona and PDF calls counted; no per-request ledger |

## Planned next rounds

| Round | Content |
|---|---|
| 17 (rest) | Dependency and AGP/compileSdk upgrade, ChromeOS ABI, web-extract and rules-engine decisions |
| 18 | Device and deploy verification (needs a phone and AWS access) |
