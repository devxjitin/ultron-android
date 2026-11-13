# Plan: adding the Decisions API to ULTRON

Updated 2026-11-13. This started as a plan and is now a record of what was built, measured and dropped; the original phase descriptions were replaced by the status section below.
Reference: https://aistudio.google.com/docs/decisions-api

## What the Decisions API does

It does not write text. You send one input (text, image, audio, PDF or video) plus up to 1,000 questions, and it returns a probability and a confidence for each:

- **Yes/no** (`noul`): `noul` is the chance of yes (0.01–0.99); `confidence = max(noul, 1 - noul)`.
- **Choice:** `criteria` maps option IDs to descriptions (2–255); returns `choice`, `probabilities`, `confidence`.
- **Score:** `criteria` is an ordered list of levels (2–255); returns `score` (weighted average index), `probabilities`, `confidence`.

Request: `POST https://generativelanguage.googleapis.com/v1beta/decisions` with header `x-goog-api-key`, body `{model: "gemini-decision-1", input, questions: {key: {type, instructions, criteria}}}`. Images take `resolution`: `low | medium | high | ultra_high`. Only input tokens are billed. v1beta: no caching, no Batch API. Keys `[A-Za-z0-9_-]{1,64}`, context 262,144 tokens.

## Phase 0 results (done 2026-11-13)

Measured with [scripts/decisions_probe.py](../scripts/decisions_probe.py), 3 runs per case, from the owner's network:

| Case | median ms | p90 ms | input tokens |
|---|---|---|---|
| text, 4 routing questions | 1082 | 1284 | 272 |
| text, 4 safety questions | 1282 | 2166 | 258 |
| screenshot `low` + 2 questions | 1770 | 1864 | 440 |
| screenshot `medium` + 2 questions | 1243 | 1679 | 701 |
| screenshot `high` + 2 questions | 1780 | 1983 | 1254 |

- 15 back-to-back calls: no 429s (not enough to find the real free-tier limit).
- One screenshot call dropped its connection once; the retry worked (the client now retries once).
- **Latency is about 1.1–1.8 s per call, not the 700 ms the first draft assumed.** Nothing can sit in front of the first spoken reply. Each switched-on check adds roughly that to the step it guards (reranking ~1.1 s, action check ~1.3 s, screen check ~2.5 s including a 0.6 s wait); not measured on a phone.

What the code shows (the first draft guessed these):

| Plan assumption | Real code |
|---|---|
| App code not in this repo | It is: `android/app/src/main/java/com/assistant/app/` |
| Tools may change per request | **No.** `GeminiLiveClient.buildSetupMessage()` sends `liveFunctionDeclarations(settings)` once at connect: knowledge search, memory, personality, skill, plus every enabled `DeviceTool` (97 entries) |
| 19 connectors | 21 `ConnectorSpec`s in `ConnectorRegistry.kt`, in 4 `ConnectorCategory` groups |
| Memory in ObjectBox | `MemoryStore` keeps notes as JSON in DataStore; duplicates use word overlap (`overlap()`, `chooseReplacement()`) |
| Document search in ObjectBox | `RagSearchEngine.search()` fuses BM25 and embedding rankings (`fuseRankings`) over ObjectBox chunks |
| kotlinx.serialization | Not used. Network code uses OkHttp 4.12 (`HttpClients.kt`) and `org.json` |
| WorkManager | Not a dependency. Background work runs in coroutines |
| Screenshots | `AssistantAccessibilityService.takeShot()` (Android 11+, returns `Unsupported` below); `readScreen()` gives the node tree; `ScreenImage.toJpeg(maxSide = 1280)`; minSdk is 26 |
| Key storage | `ApiKeyStore.getApiKey()` (encrypted prefs) |

Still open: the 200-example test set (see below).

## Can Firebase call it from Kotlin?

**No, not today.** The app already uses Firebase (BoM 35.1.0: Auth, App Check with Play Integrity, Crashlytics, Analytics) but not Firebase AI Logic (`firebase-ai`). Firebase AI Logic's Kotlin SDK covers generate-content, chat, function calling, grounding, Live, image and speech generation, and hybrid on-device models. Its docs do not list the Decisions API or `gemini-decision-1`, and its proxy only forwards the SDK's own calls, not arbitrary REST endpoints. Recheck when Decisions leaves beta.

The Google GenAI SDKs that do support Decisions are Python and JavaScript only.

**Choice:** call REST directly from the app with OkHttp and the user's own key, like `GeminiTextClient` already does. This fits the "own Gemini key" model and adds no server cost. A Lambda proxy in `infra/` would only make sense if Decisions ever ran on the owner's key, and then it should check App Check tokens.


---

## Status (2026-11-13)

| Phase | Result | Commit |
|---|---|---|
| 0 Groundwork | Probe run, tool-list size measured. The 200-example test set is **not** done (starter rows only). | `09d4db2` |
| 1 Shared base | Built: client, models, question builder, cache, retry, log. Per-feature thresholds are constants in each checker, not in settings. | `09d4db2` |
| 6 Rerank document search | Built, switch off by default. | `c8c49c1` |
| 5 Tidy memory | Built (duplicates and contradictions only), switch off by default. | `1c68534` |
| 3 Check screen actions | Built for `click_on_text` and `type_text`, switch off by default. | `9f1b2d3` |
| 4 Check screen after a step | Built (Android 11+ screenshots only), switch off by default. | `e994c10` |
| 7 Smaller additions | Skill matching built (error path only). Document tagging **dropped**. | `55381e6` |
| 2 Lighter tool lists | **Dropped** after measuring (see below). | none |

Nothing is pushed. Every feature is off by default and has its own switch in Settings. Nothing has been run on a phone. All thresholds are untuned guesses until the test set exists. A new `## x.y.z` entry in `changelog.md` is needed before a PR (CI checks it).

There is no master "Use Decisions API" switch and no "lite mode" yet; each feature has its own toggle instead.

## What was built

Package `com.assistant.app.decisions` (`android/app/src/main/java/com/assistant/app/decisions/`):

| File | What it does |
|---|---|
| `DecisionsModels.kt` | `YesNo`, `Choice`, `Score` questions and answers; `questions { }` builder that checks API limits; text and image inputs. |
| `DecisionsClient.kt` | REST call with OkHttp and the user's key in a header. Returns `Answered` or `Failed(reason)`, never throws. One retry on a dropped connection, per-call timeout, in-memory cache (64 entries), sets `rateLimited` after a 429, one log line per call without the input text. |
| `ChunkReranker.kt` | Scores the top 20 search chunks (0–3 each), keeps up to 4 scoring ≥ 1.5. Null on failure so the old ranking is used; empty means nothing answers the question. |
| `MemoryTidier.kt` | Asks `dup_N` and `con_N` for each stored note after a save; removes older notes with probability and confidence ≥ 0.85, into the restorable log. |
| `ActionChecker.kt` | Before `click_on_text` and `type_text`: blocks when `matches_user_request` < 0.3 (confidence ≥ 0.7); adds a `warning=` line when a risk flag > 0.6; otherwise proceeds. Fails open. Skipped while a password field has focus. |
| `ScreenChecker.kt` | After a successful step: `step_succeeded`, `blocker`, `next` from a 720 px low-resolution screenshot, added to the result as a `screen_check:` line. After 3 failures in a row the advice becomes "stop". |
| `SkillMatcher.kt` | When `use_skill` names a skill that does not exist, picks the closest skill (confidence ≥ 0.7) and says so in the result. |

Hooks: `RagSearchEngine` (optional `rerank`), `MemoryStore.removeOutdated`, `ToolRunner` (`checkAction`, `checkScreenAfter`, `suggestSkill`, `onMemorySaved`), `AssistantViewModel` (builds each checker with the user's key), `VoiceSessionController.currentUserRequest()`, `AssistantAccessibilityService.focusedFieldIsPassword()`. Settings: `decisionsRerankEnabled`, `decisionsMemoryEnabled`, `decisionsActionCheckEnabled`, `decisionsScreenCheckEnabled`, `decisionsSkillEnabled` in `AppSettingsStore`, five toggles in the Settings dialog.

Tests: 12 client, 6 reranker, 7 memory, 8 action, 6 screen, 4 skill tests in `android/app/src/test/java/com/assistant/app/decisions/` (all use a mock server). The full unit suite passes.

## What changed from the first draft

- **Per request tool lists (old Phase 2): dropped.** Measured with `countTokens` on the exact JSON sent at setup: 97 device tools = about 8,750 tokens, the default 46 = about 4,260, system prompt about 2,070. The context window is 65,536 tokens, so the worst case is about 13% once per session. Live fixes tools at connect, and a Decisions call adds ~1.1 s to every tool call, so it would cost more than it saves. Revisit only if the model picks wrong tools because it has too many (an accuracy problem nobody has reported).
- **Document tagging: dropped.** Nothing in the app would use a document type or a sensitive flag, it needs an ObjectBox schema change, and document text is already sent to Gemini for embeddings.
- **Memory:** `worth_saving`, `importance` and "drop the least important when full" were not built. `MemoryItem` has no importance field and the voice model already decides what to save.
- **Screen check on Android 8–10:** does nothing. Reading the screen text as a fallback would call `readScreen()`, which renumbers the list `click_item` uses.
- **Not checked by the action check:** taps by coordinates, swipes, Enter and `click_item`, because they carry no visible text to judge.
- **Safety stays hands-free (decided 2026-11-13):** money actions in UPI and banking apps get no spoken "yes" and no tap, only the warning line.

## Still to do

1. **Fill the test set** ([docs/decisions-testset/](decisions-testset/)): about 200 labelled examples (routing 80, safety 50, screens 40, memory pairs 30). Use it to tune the thresholds above, which are guesses today. Only the safety and memory files are directly usable by the current features; routing examples were for the dropped Phase 2.
2. **Test on real phones** (Android 8, 11, 14): measure real added latency, check the blocked and warning paths, and check the screen check is skipped for protected apps and password screens.
3. **Add a changelog version** before a PR, then roll out to beta users with the switches off, turn on one at a time and read the log lines.
4. **Privacy text:** add a line in onboarding and the privacy policy that screen text, screenshots, notes and document passages can go to Google for these checks when a switch is on (each switch's hint already says so).
5. **Maybe later:** a master switch and "lite mode"; thresholds in developer settings; the memory importance score; recheck Firebase AI Logic when Decisions leaves beta.
6. **Website:** once features ship, add them to the feature list in `sites/ultron-showcase`.

## Risks

| Risk | How to handle it |
|---|---|
| Beta API changes or is retired | All calls inside `DecisionsClient`, each feature has its own off switch, every feature falls back to today's behaviour |
| ~1–2 s per call | Every feature is opt-in; none sits in front of the first reply |
| Free-tier limits on users' keys | Cache, `rateLimited` flag, failures fall back silently |
| Checks feel like friction | Block only clear mismatches; no confirmations or taps (owner's decision) |
| Memory tidy removes a note wrongly | High threshold, removed notes stay in the restorable log |
| Reranker says "nothing found" wrongly | Threshold of 1.5 is a guess; tune on the test set before turning on for everyone |
| Screenshots leaving the phone | Optional, low resolution, never for protected windows or password screens |
