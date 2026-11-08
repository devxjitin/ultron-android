**Ultron project review — 21 October 2026**

Reviewed checkout: `bf47ffc`. This is a source and local-test audit of the Android application, Python MCP services, infrastructure, and release tooling. Application behavior was not changed. Findings below describe the checked-in code, not a verified production deployment.

**Result and limits**

There are 37 actionable findings below, including correctness bugs, data-loss paths, latency problems, connector limitations, and dead functionality. P1 means fix before broader distribution; P2 means a meaningful reliability or feature defect; P3 means cleanup. Findings are source-traced unless explicitly identified as locally reproduced or documentation-verified. Risk scenarios are not claims that an exploit or production failure was observed.

The Android unit suite passed: **134 tests, 19 suites, zero failures, errors, or skips**. The initial test invocation found a stale SDK path; setting `ANDROID_HOME` to the installed SDK allowed the suite to finish without editing local configuration. Existing tests primarily cover retrieval, settings, audio math/callback gates, and error/latency utilities. They do not establish end-to-end correctness of the main features.

**Android lint failed with 7 errors and 25 warnings.** The offline run first lacked a cached dependency; the subsequent online run completed analysis and produced a fresh [lint report](X:/KIRA/android/app/build/reports/lint-results-debug.html). These are diagnostics, not 32 independently confirmed runtime bugs:

| Diagnostic | Triage and fix |
|---|---|
| Two Bluetooth MissingPermission errors, `DeviceToolRunner.kt:750` | A custom permission helper and outer SecurityException handler already exist. Make the check locally recognizable to lint, and check permission before `adapter.isEnabled` as well. Test denied/revoked access; do not claim these diagnostics alone prove a crash. |
| Two NewApi errors, light navigation-bar attributes in both base themes | Put API-27-only attributes into `values-v27` and `values-night-v27` overrides while retaining API-26 base themes. |
| Two ProtectedPermissions errors, WRITE_SETTINGS and PACKAGE_USAGE_STATS | These features use special user-granted settings access. Preserve the required declarations and flows; document intentional use and narrowly suppress the diagnostic only after verifying `Settings.System.canWrite`/usage-access checks. Removing the permissions is not the fix. |
| Missing optional telephony hardware declaration | Add `android.hardware.telephony` with `required=false` if tablets/Chromebooks are intended to install the app. |
| SelectedPhotoAccess warning | Support Android 14 partial-library access and reselection; distinguish access to selected photos from full-library access in the connector UI. |
| Remaining warnings | Review newer dependency compatibility rather than blindly upgrading; add explicit backup/transfer rules, remove confirmed unused colors/round icon, add monochrome launcher artwork, and address minor Compose/ABI/storage diagnostics according to supported devices. |

The Gradle run also warns that AGP 8.5.2 was tested only through compileSdk 34, while this project compiles against 35. Upgrade the build toolchain as a tested unit. Do not hide the whole lint result with a broad baseline as a substitute for triage.

[Offline reproductions](X:/KIRA/docs/audit_reproductions.py) exercise the actual Python handlers with mocked outbound calls. They confirm malformed JSON-RPC crashes, rejection of base64-encoded request bodies, and missing Gmail reply headers. The script also replays the Kotlin text-display algorithm; its timings are deliberate delays, not phone benchmarks.

No live Google/GitHub account mutations, AWS deployment, model requests, downloads, microphone sessions, or physical-device walkthroughs were performed. Actual p50/p95 latency, battery consumption, audio quality, provider billing, TalkBack behavior, and small-screen layout still need device validation. This review covers every major feature family; it is not a claim that every possible execution path is bug-free or that every screen was visually tested.

**Highest-priority findings**

**F01 · P1 · New cloud deployments lack a required Function URL permission.**

[infra/ultron.yaml:68](X:/KIRA/infra/ultron.yaml:68) and the corresponding Updates permission grant `lambda:InvokeFunctionUrl` only. New Function URLs also require `lambda:InvokeFunction`; fresh deployments can return access denied before the handler runs. Existing manually provisioned resources may have additional permissions and were not inspected. Add a second resource-policy permission for each function, restricted with `InvokedViaFunctionUrl: true`. Verify a newly deployed stack with an unauthenticated request reaching the handler's own secret-path check. This requirement is confirmed by [AWS documentation](https://docs.aws.amazon.com/lambda/latest/dg/urls-auth.html).

**F02 · P1 · Reminders fail unless an unrelated connector is enabled.**

[DeviceToolRunner.kt:250](X:/KIRA/android/app/src/main/java/com/assistant/app/data/DeviceToolRunner.kt:250) schedules reminders as `SHOW_NOTIFICATION`. [AutomationEngine.kt:172](X:/KIRA/android/app/src/main/java/com/assistant/app/data/AutomationEngine.kt:172) refuses that action unless its tool is enabled, but it belongs to Apps & settings, while reminders belong to Alarms & notes. A user installing only Alarms & notes gets a successful scheduling reply followed by a disabled-tool result instead of the reminder. Persist a distinct reminder operation, authorize it against the reminder capability, and check notification permission when scheduling. Test a fresh installation with only Alarms & notes enabled, including reboot delivery.

**F03 · P1 · Cancelling chat does not promptly cancel its HTTP request.**

[GeminiChatClient.kt:134](X:/KIRA/android/app/src/main/java/com/assistant/app/network/GeminiChatClient.kt:134) and [AzureChatClient.kt:105](X:/KIRA/android/app/src/main/java/com/assistant/app/network/AzureChatClient.kt:105) register `invokeOnCompletion { call.cancel() }` on the job currently performing blocking `execute()` and `readUtf8Line()`. Completion waits for that body to exit, and the handler is disposed in `finally`. A stalled stream can therefore continue until network activity or timeout, despite New chat or cancellation. Implement an OkHttp asynchronous bridge using `suspendCancellableCoroutine` and `invokeOnCancellation`, covering both connection and body streaming. Verify a mock server that accepts a request but never sends a line: cancellation should close the socket promptly. Kotlin documents the existing hook as a [completion handler](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/invoke-on-completion.html).

**F04 · P1 · A cancelled reply can unlock a newer chat request.**

[AssistantViewModel.kt:585](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:585) unconditionally sets `isChatSending = false` in the old job's `finally`. New chat/open chat cancels without joining, and permits another send immediately. If the old blocking request exits after the new send starts, it clears the new request's sending flag, permitting overlapping sends and inconsistent persistence. Give each request an immutable session/request identity; only its owner may update the reply, save a turn, or clear sending state. Test A stalled → New chat → send B → A exits, and assert B remains sending.

**F05 · P1 · Memory replacement can delete unrelated facts.**

[MemoryStore.kt:66](X:/KIRA/android/app/src/main/java/com/assistant/app/data/MemoryStore.kt:66) deletes every note with word overlap of at least 0.5, measured against the smaller word set. A replacement hint such as `Delhi` matches both a home address and an unrelated fact about a relative living in Delhi; each scores 1.0. Replace notes by explicit stable IDs returned with relevant memories. If matching is necessary, propose candidates and avoid destructive changes on ambiguous matches. Test multiple people sharing a city, negation, short hints, and multilingual facts. Keep a small undo history for model-originated memory changes.

**F06 · P1 · DOCX size limits do not bound decompressed content.**

[DocxText.kt:23](X:/KIRA/android/app/src/main/java/com/assistant/app/data/DocxText.kt:23) calls `zip.readBytes()` on `word/document.xml`, then allocates several replacement strings before applying `take(maxChars)`. A small compressed file can expand far beyond the 10 MB attachment cap and exhaust memory. Use a bounded decompression stream and streaming XML parser; enforce expanded-byte, entry, and text limits before allocations. Test a highly compressible large XML fixture without allocating an unsafe amount in the test process. Report an explicit size/format error rather than treating failed extraction as an empty document.

**F07 · P1 · Link preview address checks are separate from the connection's DNS lookup.**

[LinkPreviews.kt:246](X:/KIRA/android/app/src/main/java/com/assistant/app/data/LinkPreviews.kt:246) checks `InetAddress.getAllByName`, then connects with an OkHttp client that resolves the hostname independently. A changed DNS answer can bypass the public-address check and contact a private destination. The Python web extractor already connects to the checked IP; apply that same principle on Android with a validated DNS implementation tied to the actual connection, including redirect hops and alternate addresses. Test a controlled resolver that changes answers. Also validate all non-global/reserved address ranges. No live attack was attempted.

**F08 · P1 · Concurrent YouTube downloads overwrite each other's temporary files.**

[YouTubeDownloader.kt:106](X:/KIRA/android/app/src/main/java/com/assistant/app/data/YouTubeDownloader.kt:106), lines 120–121 and 145 use fixed names such as `yt-video.tmp`; line 356 uses a shared unrestricted IO scope. Two downloads can truncate, mux, or delete the same files. Give every download a unique working directory and persistent job ID, bound concurrency, and clean up only that job's files. Test two simultaneous downloads, one failure, and one cancellation; verify both resulting tracks and titles. A notification alone also does not make a coroutine durable across process death; use an appropriate foreground download worker/service.

**F09 · P1 · Corrupt chat metadata can cause permanent attachment deletion.**

[ChatHistoryStore.kt:144](X:/KIRA/android/app/src/main/java/com/assistant/app/data/ChatHistoryStore.kt:144) turns any decode error into an empty list. The startup collector in [AssistantViewModel.kt:328](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:328) interprets the list as authoritative and prunes attachment directories. One malformed session can hide all chats, delete their files, and let the next save overwrite history. Distinguish empty from corrupt data, quarantine the unreadable payload, offer recovery/export, and prohibit pruning until a successful validated read. Test corrupt JSON and a single malformed session among healthy sessions.

**Chat, latency, cost, and storage findings**

**F10 · P2 · Artificial text pacing delays completion and drives excess recomposition.**

[AssistantViewModel.kt:517](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:517) reveals 2–12 characters every 16 ms and waits for the revealer before saving or accepting another message. The offline replay yields 1.952 seconds for 500 already-buffered characters, 7.952 seconds for 5,000, and 14.608 seconds for 10,000. [Markdown.kt:88](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/Markdown.kt:88) reparses the growing response on each text change, adding work on the UI thread. Display provider deltas in batches every roughly 30–60 ms, flush immediately on completion, and reuse completed Markdown blocks. Measure first-visible-token and provider-complete-to-UI-complete separately.

**F11 · P2 · Chat search performs allocation-heavy full-history scans on the UI thread.**

[ChatSearchPage.kt:65](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/ChatSearchPage.kt:65) rebuilds and lowercases transcripts and uses `haystack.windowed(token.length).count` for every token; line 103 runs this inside `remember` during composition. Large histories can cause typing lag and garbage collection. Debounce query changes, run cancellable searches on `Dispatchers.Default`, replace window allocations with `indexOf` counting, then move to an indexed store if needed. Test the maximum retained history and verify input remains responsive while queries change quickly.

**F12 · P2 · Several voice tools block the main thread.**

[AssistantViewModel.kt:2398](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:2398) launches voice tools on `Dispatchers.Main`. Many branches in [DeviceToolRunner.kt:111](X:/KIRA/android/app/src/main/java/com/assistant/app/data/DeviceToolRunner.kt:111) synchronously query contacts, calendars, photos, app usage, or local JSON. Slow providers/storage stall animation and delay main-thread voice callbacks. Assign threading in each implementation: IO for provider/file work, main only for UI-affine actions. Use StrictMode and a slow fake provider to test screen responsiveness and audio callback scheduling.

**F13 · P2 · Token accounting omits auxiliary model work and provider routing is surprising.**

[AssistantViewModel.kt:802](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:802) invokes Gemini to title chats after the first turn and every third turn. This also runs for Azure chats when a Gemini key exists. Persona generation, PDF formatting, embeddings, and fallback requests are not recorded by the chat usage path. Cancelling a reply skips recording already accumulated usage, and requests failing before the terminal usage callback can lose counts. Route titles through an explicitly selected provider, make automatic titling configurable, and centralize usage by request/model/purpose, including failed/cancelled/unknown usage. Keep voice-time metrics visibly separate from billed token usage. Verify a one-turn Azure chat and a PDF import against a mocked request ledger.

**F14 · P2 · Context is limited by message count and file bytes, not token cost.**

[AssistantViewModel.kt:653](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:653) keeps 24 messages without a text budget. Both chat clients resend retained attachments and accumulated tool results on each of up to six model requests. The request builders have no explicit output budget. Long pasted text, documents, and multiple tools therefore cause expensive repeated context or provider-limit failures. Set per-model input/output budgets, preserve complete user/assistant exchanges, reserve space for tool results, and summarize or retrieve older content. Offer only relevant tool groups for a turn. Use provider file references where supported; they reduce transfer cost but do not by themselves eliminate token billing. Test a large paste plus several tool rounds and assert an enforced budget.

**F15 · P2 · Editing knowledge silently replaces the manually configured voice prompt and blocks the app.**

[AssistantViewModel.kt:1694](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:1694) regenerates a persona after every document mutation; [AppSettingsStore.kt:296](X:/KIRA/android/app/src/main/java/com/assistant/app/data/AppSettingsStore.kt:296) writes it over the same key used by manual settings. Deleting the final document resets that prompt. The UI displays a non-dismissible Updating dialog; the 60-second coroutine timeout cannot interrupt `GeminiTextClient`'s blocking network call, whose read timeout is 120 seconds. Separate manual instructions from generated knowledge guidance, make updates opt-in or reviewable, and use a cancellable background job with non-blocking status. Test preservation of a custom prompt after document add/delete and a stalled provider.

**F16 · P2 · Editing/trimming turns leaks attachment files.**

[ChatHistoryStore.kt:46](X:/KIRA/android/app/src/main/java/com/assistant/app/data/ChatHistoryStore.kt:46) drops turn references without deleting files; the 200-turn cap does the same. [ChatAttachmentStore.kt:47](X:/KIRA/android/app/src/main/java/com/assistant/app/data/ChatAttachmentStore.kt:47) prunes only whole missing-session directories. Orphaned files inside retained chats remain invisible to Library and its reported storage totals. Reconcile the set of referenced attachment paths, with a grace period for in-flight saves; perform this after confirmed metadata commits. Test editing an old attachment turn and crossing the retention cap, then compare referenced bytes with actual disk usage.

**F17 · P2 · Saved conversations are silently evicted.**

[ChatHistoryStore.kt:24](X:/KIRA/android/app/src/main/java/com/assistant/app/data/ChatHistoryStore.kt:24) keeps 50 sessions and 200 turns per session, despite the store comment saying history is only explicitly removed. Continuing an old chat does not move its position, so it can still be evicted when a new session is added. Use a durable database and an explicit retention policy with export/archive options. If limits remain, disclose them and evict by an agreed criterion rather than insertion order. Test session 51 and turn 201, including a recently active old chat.

**F18 · P2 · Composer drafts are cleared on rejection and are not scoped to a chat.**

[ChatHome.kt:159](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/ChatHome.kt:159) owns its input and pending files locally; its send callback returns `Unit`, and the composer clears immediately even if [AssistantViewModel.kt:477](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:477) rejects sending for a missing key. The component has no session key, so switching chats while it remains composed retains another chat's draft; navigating away can lose pending attachments. Keep per-chat drafts in a state holder and clear only on an explicit accepted-send result. Test missing-key send, new/open chat with a draft, navigation, and process recreation.

**F19 · P2 · Several navigation destinations bypass the unsaved Developer guard.**

[AssistantScreen.kt:231](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/AssistantScreen.kt:231) calls search, library, personalization, usage, storage, and about navigation directly, while only some destinations use `guardedNav`. These destinations replace `SettingsPage`, whose fields use `remember`, losing unsaved edits without the advertised Save/Discard prompt. Guard every exit from the draft owner, or retain drafts independently of screen composition. Apply the same policy to personalization text edits. Test each drawer destination from a dirty Developer form.

**F20 · P2 · Preview failures look like endless loading; image decoding is not consistently bounded.**

[AttachmentPreview.kt:168](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/AttachmentPreview.kt:168) maps PDF-open failure to null, which the UI treats as loading indefinitely; image/video failures have similar states. PDF constructor failures can leave a temporary file/descriptor unclosed. Separately, [ChatHome.kt:627](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/ChatHome.kt:627) samples thumbnails only while both dimensions exceed the threshold, and [LinkPreviews.kt:149](X:/KIRA/android/app/src/main/java/com/assistant/app/data/LinkPreviews.kt:149) considers width only. Very tall/narrow or panoramic images can allocate far more than intended. Use Loading/Ready/Error states, exception-safe resource ownership, and maximum decoded pixels plus edge limits. Test encrypted/invalid PDFs, missing files, bad images, and extreme aspect ratios.

**Connector and backend findings**

**F21 · P2 · Expired Google tokens stay cached and error recovery is incomplete.**

[AssistantViewModel.kt:1143](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:1143) assumes a token is usable for 50 minutes after receipt. `runGoogleTool` marks a specific embedded 401 message as broken but does not evict the token or refresh it; `refreshGoogleTools` discards MCP failures. HTTP-level MCP 401/403 failures are not consistently reflected in Google connector health. Evict rejected tokens, obtain fresh authorization, and retry a read once. For writes, retry only when the failure proves no action ran, or use idempotency/reconciliation. Return structured auth errors from the server. Test cached stale tokens, revoked consent, insufficient scope, and offline behavior separately.

**F22 · P2 · MCP SSE responses wait for the entire connection to close.**

[McpClient.kt:120](X:/KIRA/android/app/src/main/java/com/assistant/app/network/McpClient.kt:120) calls `response.body.string()` before `pickEvent`. A server that sends the matching response but leaves the stream open is not answered promptly; the client can hit its read timeout despite already having the result. Parse SSE incrementally, handle multiline events, stop on the matching response ID, and cancel the call with the coroutine. Validate negotiated protocol/session handling as part of the same compatibility tests. Test JSON responses, notifications preceding a result, and a result followed by a still-open stream.

**F23 · P2 · MCP schema conversion silently changes valid argument types.**

[McpSchema.kt:18](X:/KIRA/android/app/src/main/java/com/assistant/app/network/McpSchema.kt:18) drops references, selects the first non-null union branch, and turns deep or untyped nodes into strings. A tool expecting a referenced object or a different valid union branch gets an inaccurate declaration; Azure also receives this already-reduced schema. Keep original JSON Schema for compatible providers, resolve local references, and explicitly mark/reject unsupported tools instead of silently changing types. Test reference, nullable, union, dictionary, and nested-object schemas against representative connector fixtures. This is a converter defect; affected live GitHub schemas were not fetched.

**F24 · P2 · Google list/read tools can silently return incomplete datasets.**

[Google Workspace handler:104](X:/KIRA/mcp/google-workspace/lambda_function.py:104), Gmail search, and Drive searches discard pagination tokens. Their tool schemas offer no continuation token. The Android layer further truncates all MCP output at 6,000 characters, even where the server returns a larger document or sheet range. A user cannot reliably exhaust results or read a later portion through those tools. Return structured items, `next_cursor`, and truncation metadata; support bounded continuation and range/offset reads. Test more events/files/messages than one page and a document longer than 6,000 characters. Keep summaries explicit about partial coverage.

**F25 · P2 · Google Docs reads omit all but the first document tab.**

[Google Workspace handler:337](X:/KIRA/mcp/google-workspace/lambda_function.py:337) reads `document.body` without `includeTabsContent`. Appending similarly assumes one body and supplies no tab selector. Request tab content, recurse through child tabs, return tab IDs/titles, and require/select a tab for writes. Verify a document whose relevant information exists only in a second or nested tab. The first-tab fallback is documented by [Google Docs](https://developers.google.com/workspace/docs/api/how-tos/tabs).

**F26 · P2 · Gmail's reply-draft tool does not satisfy the threading contract.**

[Google Workspace handler:224](X:/KIRA/mcp/google-workspace/lambda_function.py:224) sets `threadId` but never sets `In-Reply-To` or `References`, nor derives the subject from the original message. The mocked reproduction confirms those headers are absent. Fetch the parent message metadata and construct a proper reply with matching subject and RFC message references; keep draft-only behavior. Verify the returned draft belongs to the intended thread. [Google's message reference](https://developers.google.com/resources/api-libraries/documentation/gmail/v1/python/latest/gmail_v1.users.threads.html) specifies the threading requirements.

**F27 · P2 · MCP handlers crash on valid JSON that is not a valid RPC object.**

[Google Workspace handler:484](X:/KIRA/mcp/google-workspace/lambda_function.py:484) and [web-extract handler:250](X:/KIRA/mcp/web-extract/lambda_function.py:250) validate JSON syntax only. Values such as null, 42, or a batch containing null reach `.get` and raise `AttributeError`. Both also ignore `isBase64Encoded`, rejecting otherwise valid encoded bodies. These paths were reproduced offline. Validate envelope/params/arguments types and supported batch semantics before dispatch; decode the Lambda body envelope and return proper parse/invalid-request/invalid-params errors. Bound request/batch size. Test malformed members without failing an entire valid batch.

**F28 · P2 · Timeout budgets do not fit multi-step connector operations.**

[McpClient.kt:28](X:/KIRA/android/app/src/main/java/com/assistant/app/network/McpClient.kt:28) permits 25 seconds of read inactivity; [infra/ultron.yaml:57](X:/KIRA/infra/ultron.yaml:57) gives Google 30 seconds total, while each upstream call can take 20 seconds. Gmail searches perform a list call followed by several waves of metadata calls; Docs append and calendar RSVP also use multiple requests. Slow valid operations exceed the outer budget. Establish one operation deadline, derive upstream timeouts from remaining time, reduce N+1 calls where possible, and return explicit partial/read-timeout outcomes. For writes, reconcile uncertain outcomes before retrying. Test delayed upstream calls, especially a successful write followed by a response timeout.

**F29 · P2 · Notes are neither atomically written nor locked across store instances.**

[NoteStore.kt:50](X:/KIRA/android/app/src/main/java/com/assistant/app/data/NoteStore.kt:50) directly overwrites JSON and converts decode failures to empty data. Its `@Synchronized` methods lock an instance, while automation creates separate runners/stores. A crash during write or overlapping instances can lose notes. Use an atomic file with a process-wide read-modify-write lock, or a transactional database; preserve corrupt data for recovery. Test concurrent additions through separate instances and interruption before commit. Do not reuse user-visible note IDs after deletion if a stale model call can still reference them.

**F30 · P2 · Clear cache can remove active downloads and previews.**

[AssistantViewModel.kt:1425](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:1425) deletes every cache entry; its only guard is visible update progress. Silent update prefetch, YouTube temporary files, and preview files can still be active. Move active work into owned job directories, track leases, and clear only inactive disposable files. Cancel/join jobs explicitly when the user asks to clear their data. Test cache clearing during silent update download, muxing, and an open video preview.

**F31 · P1 · Calling an ambiguous contact chooses the first match immediately.**

[DeviceToolRunner.kt:175](X:/KIRA/android/app/src/main/java/com/assistant/app/data/DeviceToolRunner.kt:175) finds up to five matches, launches `ACTION_CALL` for the first, and only then includes other matches in the result. A partial/common name or multiple numbers can call the wrong person. Return candidates when the target is ambiguous and require a stable selected contact/number for the call. A dialer preview is a useful fallback. Test two contacts sharing a name and one contact with multiple numbers; neither should be called without disambiguation.

**F32 · P2 · Voice errors undercount or misattribute session time.**

[AssistantViewModel.kt:1373](X:/KIRA/android/app/src/main/java/com/assistant/app/viewmodel/AssistantViewModel.kt:1373) records duration on `stopSession`, but error paths call `releaseResources` and set ERROR without recording it. Starting again overwrites the start time, losing the failed session; a later stop can instead include time spent on the error screen. Record an interval when active streaming ends, idempotently, while preserving the distinction between reconnects and new sessions. Test disconnect exhaustion, microphone failure, explicit stop, and restart after an error. Persist usage through an owner whose scope remains alive during ViewModel cleanup.

**F33 · P2 · The release script's default JDK path contains a literal newline.**

[publish-update.ps1:13](X:/KIRA/scripts/publish-update.ps1:13) splits the interpolated path across lines and spells the next segment `ea_tools`. With `JAVA_HOME` unset, the intended installed JDK is not found and the script falls back to Docker, even when the local toolchain is present. Build the path with `Join-Path` and validate prerequisites before building. Test unset `JAVA_HOME`, explicit `-Jdk`, missing Docker, and a normal local build. Also replace minute-based release IDs with a monotonic CI/build sequence to avoid same-minute version collisions; do not run publication just to validate this fix.

**F34 · P2 · Streams can be saved as successful without a successful terminal outcome.**

[GeminiChatClient.kt:150](X:/KIRA/android/app/src/main/java/com/assistant/app/network/GeminiChatClient.kt:150) and [AzureChatClient.kt:122](X:/KIRA/android/app/src/main/java/com/assistant/app/network/AzureChatClient.kt:122) silently skip malformed chunks, treat EOF as completion, and do not classify finish reasons, safety outcomes, or error events. A partial response cut off by a clean EOF or output limit can be persisted as a complete answer; a blocked result becomes an unrelated generic empty-response message. Parse terminal events explicitly and return a typed outcome with partial text, usage, and reason. Test truncation, error events, blocked responses, empty content, and normal completion. Retry generation only in a way that cannot replay already-executed mutations.

**F35 · P2 · Revoked local permissions hide installed connectors instead of showing repair state.**

[ConnectorsPage.kt:270](X:/KIRA/android/app/src/main/java/com/assistant/app/ui/components/ConnectorsPage.kt:270) defines a local connector as connected only when both enabled and permission-granted; the Installed filter then removes it when permission is revoked. Its tools can remain enabled and advertised to the model. The Location catalog also treats fine location as required even though coarse location is useful. Track installation, enabled tools, permission status, and health separately. Keep installed entries visible with a Repair action and support approximate location explicitly. Test permission revocation from Android settings and denial during initial setup.

**F36 · P3 · General automation code is unreachable from the current app surface.**

[AutomationEngine.kt:69](X:/KIRA/android/app/src/main/java/com/assistant/app/data/AutomationEngine.kt:69) contains list/cancel schedule and add/list/delete rule operations, but the current DeviceTool declarations and UI provide no callers for them. Only reminder scheduling reaches the engine. [DeviceTool.kt:42](X:/KIRA/android/app/src/main/java/com/assistant/app/data/DeviceTool.kt:42) has an unused `automationToolParams`; the allowlist still names removed weather/news/exchange-rate/website tools. `ReminderReceiver` has no current scheduling caller, although older installed alarms may still target it. Either restore explicit manageable automation features with tests or remove unreachable machinery after checking upgrade compatibility. Users currently cannot list/cancel reminders through the provided tool surface. Do not delete receivers blindly during an upgrade.

**F37 · P2 · Retrieval can combine vectors from a new document revision with old chunk text.**

[RagSearchEngine.kt:78](X:/KIRA/android/app/src/main/java/com/assistant/app/data/RagSearchEngine.kt:78) loads chunks, suspends for query embedding, then separately requests nearest chunks. A concurrent document edit can commit between those steps. Ranking uses the new index, but final lookup uses the earlier `chunks.associateBy` with the same document/chunk keys, yielding stale text or dropping new chunks. Carry a revision through the whole search and either query/assemble from one coherent snapshot or detect a revision change and retry assembly/ranking without needlessly re-embedding the query. Test an edit deliberately inserted between embedding and nearest-neighbor lookup.

**Architecture assessment and targeted improvements**

The primary architectural bottleneck is ownership: a roughly 3,000-line ViewModel owns voice sessions, chat jobs, persistence, connector auth, downloads, settings, knowledge imports, usage, and navigation. Many independent jobs mutate one large state object and mutable session IDs. That makes cancellation, state races, and testing harder than the individual features require.

| Area | Suggested change | Benefit and validation |
|---|---|---|
| Chat orchestration | Extract a ChatController with immutable request/session IDs and a provider interface; keep tool execution separate from transport. | Prevent stale writes and test stream/cancel/tool-round behavior with fake providers. |
| Voice lifecycle | Move active audio/session ownership to the foreground service or a dedicated session controller; expose a state machine to the UI. | Activity/ViewModel lifetime no longer determines the call. Test reconnect, background, mute, interruption, and process recovery separately. |
| Persistence | Use transactional rows for chats, turns, attachment references, notes, and jobs; retain DataStore for small settings. | Avoid rewriting/decoding all history on every turn/title change and make deletion atomic. |
| Knowledge storage | Consolidate canonical documents/chunks in one transactional store, with explicit revision tracking and incremental index updates. | Current source JSON plus ObjectBox copy forces broad serialization and full index replacement after mutations. Preserve the existing consistency tests during migration. |
| Knowledge import | Batch or moderately parallelize embeddings; deduplicate unchanged chunks by content hash; checkpoint large imports. | Avoid one serial network round trip per chunk and repeated cost after failures. Add byte/token/chunk budgets and clear progress/cancel controls. |
| Retrieval | Add offline BM25 fallback when embedding service is unavailable, a short-lived query cache keyed by model/revision, and source/chunk links in answers. | Faster repeat queries, graceful outages, and answers the user can verify. Keep quality thresholds explicit. |
| Tool registry | Define schema, surface, permissions, enabled state, risk, timeout, result limit, and idempotency in one registry. | Avoid capability drift across DeviceTool, PhoneConnectors, UI catalog, MCP conversion, and dispatch. |
| Tool execution | Parallelize only independent reads with bounded concurrency; preserve ordering for mutations and dependencies. | Reduce latency without creating conflicting writes or phone actions. |
| Cost control | Introduce a request ledger with model, purpose, input/output/cache/reasoning usage when reported, duration, retries, and uncertainty. | Explain the difference between visible chat usage and provider billing. Add a daily budget and opt-in auxiliary generation. |
| UI state | Use per-screen state holders, a single navigation model, persistent per-chat drafts, and lifecycle-aware collection. | Reduce broad recomposition and make navigation/draft behavior predictable. |
| Downloads/updates | Persistent jobs, unique staging paths, resumable transfer where available, explicit cancel/retry, and checksum-before-publish/install. | Survive background execution and avoid cache races. Android 8/9 downloads currently go to app-specific external files despite the public Downloads wording; offer an accurate location/share action or a document destination picker. |
| Backend | Shared validated RPC transport, deadline propagation, structured errors, pagination, and narrow tool-specific responses. | Simplifies all three Lambdas and makes Android recovery predictable. |
| Distribution | Keep shared billable API credentials out of distributable APKs; use user-provided keys or a metered authenticated broker. | BuildConfig strings are extractable; encrypted runtime storage does not protect build-time values. Secret URL paths are shared bearer secrets, not user-level authorization. |
| Release/reproducibility | Locked dependencies, explicit release channel/signing identity, monotonic versions, fresh-stack integration checks, and CI for tests/lint. | The debug-signed OTA variant updates debug-signed installs; it cannot update a separately release-signed install. Make that channel limitation explicit. |

**Feature-by-feature coverage and suggested UX changes**

| Feature/components | Review outcome | Specific improvement |
|---|---|---|
| Live voice, MicStreamer, AudioPlayer, foreground notification | Reviewed state transitions, warm connection, tool dispatch, interruption and cleanup. F12/F32; physical-device timing not measured. Existing callback guards and playback-drain handling are useful safeguards. | Show reconnect state and accessible mute/stop controls; collect actual speech-end-to-audio and underrun metrics before tuning buffer sizes. |
| Gemini/Azure typed chat | F03/F04/F10/F13/F14/F34. | Add Stop generating, Retry, and clear partial/error states. Make selected provider and attachment support visible before sending. |
| Edit/resend and history drawer | F04/F16/F17/F18. | Branch/retry rather than silently discard history; preserve drafts and expose retention/export. |
| Attachment picking/sharing | F06/F18/F20. Byte caps exist but do not cover decompression or decoded pixels. | Give import progress, per-file errors and a cancel action; retain an attachment draft across navigation. |
| Markdown and link cards | F07/F10/F20. Link cards can initiate network requests automatically. | Incremental rendering, accessible tables/code actions, and an optional preview-loading preference. |
| Library and storage | F16/F20/F30. Storage totals count referenced files and omit orphaned bytes. | Show actual disk categories and separate active jobs from disposable cache. Include notes/reminders in data-management UX. |
| Chat search | F11. | Debounced indexed search, scope filters, and navigation to the matching turn rather than only opening the conversation. |
| Memory | F05. Memory prompt is capped at 4,000 characters while up to 150 notes can be stored. | Explain that only selected facts reach a reply; retrieve relevant memories and show an edit/undo log. |
| Knowledge documents, PDF import, editor | F15/F37. Existing chunking/migration/index consistency tests pass. | Preserve manual prompts, expose source citations and import resumability, show embedding configuration before import. |
| Personalization/settings/developer | F15/F19. Settings persistence has focused tests; navigation and mixed immediate/manual save behavior lack coverage. | Use consistent Save/Discard semantics and show when a choice applies to the next voice session. |
| Usage page | F13/F32. `KEEP_DAYS=120` means All time is only retained activity; its chart shows 30 days. | Label retention and chart span honestly, separate estimated/known costs, and update the date across midnight while open. |
| Marketplace/Connectors | F21/F23/F35. | Separate Added, Enabled, Permission needed, Reconnect, and Service unavailable. Avoid per-tool DataStore edits when enabling one group. |
| Device info/controls | F12; permissions and platform capabilities differ by tool. | Per-tool readiness and explicit DND/modify-settings requirements rather than a blanket Connected label. |
| Alarms/reminders/notes | F02/F29/F36. | A visible list with cancel/edit/completion status and missed-delivery reasons. |
| Calls/contacts/calendar/location/photos | F12/F31/F35. Photos return filenames/counts, not visual analysis; local event creation opens a form. | Disambiguate recipients and accurately distinguish opened forms from completed changes. |
| Screen control/navigation/accessibility | Reviewed read/tap/type paths and password-field refusal. `clickItem` reuses saved coordinates with no screen identity check. | Associate observations with window/package/time and reject stale taps after screen changes; keep a visible stop control. |
| Notifications and app usage | Reviewed access gates and text-return paths. | Exclude or redact sensitive notification content where possible and make destination/provider clear. |
| DuckDuckGo | HTML/lite parsing plus instant-answer fallback is present; no live search was attempted. | Use parser fixtures, distinguish blocked/no-results/network errors, and offer a supported search-provider adapter for reliability. |
| YouTube search/info/transcripts/downloads | F08/F30. Search/playlist traversal uses the initial result page; lookup functionality was not exercised against YouTube. | Pagination, explicit truncated transcript markers with continuation, durable download jobs, and codec/quality compatibility checks. |
| GitHub OAuth/MCP | Read-only headers and toolset restrictions are present. F22/F23 and cancellation concerns remain. | Contract tests against captured public tool schemas; clear permission-versus-network errors. |
| Google Calendar | F21/F24/F28. | Cursor support and idempotent event creation/reconciliation; expose invitation side effects. |
| Gmail | F21/F24/F26/F28. Draft-only behavior is preserved in the implementation. | Thread-aware reply drafts, structured headers, and readable attachment metadata. |
| Drive | F21/F24. Native exports/text are supported, arbitrary binaries are not. | Disclose supported file types, expose pagination and shared-drive behavior, and permit bounded text ranges. |
| Docs | F25 and stale read-then-append index risk under concurrent edits. | Tab selection plus revision-aware edits or API end-of-segment insertion when suitable. |
| Sheets | Range read/update/append exist; result truncation can cut JSON mid-row. | Structured cell/range results, explicit RAW versus USER_ENTERED choice, and mutation previews for broad changes. |
| Slides | Text read/replace exists; grouped shapes are not recursively traversed by `_slide_text`. | Include grouped elements, slide-scoped replacement, and precise changed-slide reporting. |
| Web-extract Lambda | F27; checked-IP fetching is stronger than Android previews. It is not wired into the Android tool catalog or the two-function infrastructure template. | Treat it as an explicitly standalone service or add intentional app/deployment integration; pin build dependencies and architecture. |
| OTA/about/release | F01/F30/F33. SHA-256 download verification is present. | Show signing channel, durable retry/progress, and distinguish no-release from wrong URL/auth-path configuration instead of treating every 404 as up-to-date. |
| Themes, icons, toasts, layout primitives | Inspected source structure and theme selection; no rendered contrast/accessibility certification. | Validate font scale, TalkBack, landscape, narrow phones, light/dark, and keyboard insets on devices. Make full-time immersive/keep-screen-on behavior an explicit kiosk preference. |

**Dead code and misleading documentation cleanup**

F36 is the clearest unreachable feature cluster. Additional candidates include `ToolGroup.WEB` without current tools, comments that still describe chat as cloud-tools-only, the Markdown-named upload path that now accepts PDFs, and the unused legacy reminder receiver. Verify callers/upgrade needs before removal. The web-extract service is independently callable and should not be declared dead merely because the Android app does not call it.

The README also needs reconciliation with the implementation: knowledge operations require a separate Knowledge API key; storage uses DataStore as canonical knowledge source plus an ObjectBox index; general scheduling/rule management is not exposed; typed usage excludes title-generation calls; and retained history is capped. These discrepancies increase support/debugging effort even when intentional implementation choices are sound.

**Suggested fix order and acceptance checks**

1. Repair P1 defects: Function URL policy, reminder capability, cancellable transport/request ownership, destructive memory matching, archive/image resource limits, checked connection DNS, unique download paths, corrupt-store handling, and contact ambiguity.
2. Add regression coverage for those scenarios before restructuring. Keep the passing retrieval/settings tests. Add MockWebServer tests for stream termination/cancellation/tool rounds; fake-provider tests for chat state; mocked backend tests for schema/auth/deadline/pagination; and Compose tests for draft/navigation behavior.
3. Remove artificial streaming delay, move search/provider IO off main, and make usage visible by request purpose. Measure p50/p95 first-visible-text, provider completion versus UI completion, tool round-trip, voice startup, speech-end-to-audio, and missed frames on a representative low-memory phone.
4. Separate controllers/repositories and transactional persistence incrementally. Do not rewrite the full app before locking down the failing behavior.
5. Complete a device matrix: API 26/29/33/35+, denied/revoked permissions, approximate location, offline/slow networks, background/process death, large history, malformed documents, concurrent downloads, rebooted reminders, and release/OTA signing compatibility.

Recommended release gates: zero known P1 findings; cancellation reliably terminates blocked requests; no stale request can modify another chat; no automatic delete after corrupt reads; reminder delivery succeeds with only its own connector enabled; large history does not block input; and usage totals explain every app-initiated model request or explicitly mark usage unknown.



