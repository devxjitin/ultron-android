# Plan: adding the Decisions API to ULTRON

Updated 2026-11-13 against the real app code in `android/` and the Phase 0 probe results.
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
- One screenshot call dropped its connection once; the retry worked. The client needs one retry.
- **Latency is about 1.1–1.8 s per call, not the 700 ms the first draft assumed.** Nothing can sit in front of the first spoken reply. This reshapes Phase 2.

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

Still open: the 200-example test set. Scaffolding and starter rows are in [docs/decisions-testset/](decisions-testset/).

## Can Firebase call it from Kotlin?

**No, not today.** The app already uses Firebase (BoM 35.1.0: Auth, App Check with Play Integrity, Crashlytics, Analytics) but not Firebase AI Logic (`firebase-ai`). Firebase AI Logic's Kotlin SDK covers generate-content, chat, function calling, grounding, Live, image and speech generation, and hybrid on-device models. Its docs do not list the Decisions API or `gemini-decision-1`, and its proxy only forwards the SDK's own calls, not arbitrary REST endpoints. Recheck when Decisions leaves beta.

The Google GenAI SDKs that do support Decisions are Python and JavaScript only.

**Choice:** call REST directly from the app with OkHttp and the user's own key, like `GeminiTextClient` already does. This fits the "own Gemini key" model and adds no server cost. A Lambda proxy in `infra/` would only make sense if Decisions ever ran on the owner's key, and then it should check App Check tokens.

---

## Phase 1: Shared base (3–4 days)

New package `com.assistant.app.decisions`:

| Piece | What it does |
|---|---|
| `DecisionsClient` | REST via the shared OkHttp client in `HttpClients.kt`, `org.json` for bodies, key from `ApiKeyStore`. One retry on connection errors. |
| `Models` | `Noul`, `Choice`, `Score` questions and answers. |
| `QuestionBuilder` | Small DSL that checks key format, 1–1,000 questions, 2–255 options. |
| `DecisionPolicy` | Thresholds per feature in `AppSettingsStore` (developer settings) so they can be tuned without a release. |
| `Fallback` | Time limit per call: 2.5 s for background checks, 3 s for screen checks. Timeouts, 429 and 5xx fall back to today's behaviour. After a 429, skip optional checks for the session. |
| Cache | In-memory LRU keyed by input hash + question set, per session. |
| Flags | One switch per feature plus a master "Use Decisions API" switch. All off by default. |
| Log | Question keys, answers, confidence, latency and outcome, written next to the latency traces (`LatencyTrace`), never the input text. |

**Rule:** every feature behaves exactly as today when a Decisions call fails or is off.

**Done when:** unit tests pass against mocked responses (in `android/app/src/test/`), and a debug-only call succeeds with a test key.

---

## Phase 2: Lighter tool lists (re-scoped, do after Phase 5)

The first draft picked tools per request. That cannot work as written: Live fixes tools at connect, and a Decisions call takes ~1.1 s, longer than the user will wait before a reply.

1. **Measure first.** Count the tokens of `liveFunctionDeclarations()` with everything on. If it is small next to the system instruction and history, drop this phase.
2. **If it is worth it, use dispatcher tools.** Replace the ~97 device tools with one tool per `ConnectorCategory` group (`essentials`, `search_media`, `personal`, `phone_control`), each taking the user's intent as text. Inside `ToolDispatcher`, a Decisions `choice` question picks the real `DeviceTool`, then `DeviceToolRunner` runs it. This adds ~1.1 s to each tool call, so test whether voice still feels fast.
3. Fallback: if confidence is below the threshold, ask Live to call again with the full tool name (keep a `run_device_tool(name, args)` escape hatch).

**Done when:** the right tool is picked ≥ 97% of the time on the routing test set, setup tokens drop clearly, and tool turns are no more than ~1 s slower.

---

## Phase 3: Safety check before screen actions (1 week)

**Constraint:** the owner wants hands-free control with no extra confirmation steps (`ActionGuard` was removed on 2026-11-05). So this check **does not add a confirmation**. It blocks only clear mismatches and otherwise adds a warning line to the tool result, like `UntrustedContent` does.

**Where it hooks in:** `AssistantAccessibilityService.clickByText`, `tap`, `typeText` and `pressSendInPreview`, called from `DeviceToolRunner`. Run it in the runner just before the call.

**Input:** JSON with the front app (`currentPackage()`), the visible text of the target node or field, the planned action, and the user's last request. Text only by default (about 260 tokens, ~1.3 s); no screenshot.

**Questions:** `is_irreversible`, `sends_to_other_person`, `involves_money`, `deletes_data`, `matches_user_request`.

| Situation | Action |
|---|---|
| `matches_user_request` below 0.3 | Do not act. Return `status=blocked` with the reason, so Live tells the user. Mainly guards against instructions injected by screen or web text. |
| Any risk flag above 0.6 | Act, and add a `warning=` line to the result so Live says what it did |
| Call fails or times out | Act as today (fail open), log it |

Fixed rules: never send screen text from a password field (`node.isPassword` is already masked in `readScreen()`).

**Decided 2026-11-13:** fully hands-free. Money actions (UPI, banking) get no spoken "yes" and no tap either; they only get the warning line when a risk flag is above 0.6.

**Done when:** the safety test set shows that all injected or mismatched actions are blocked, under 2% of normal actions are blocked, and added latency per action is ~1.3 s or less.

---

## Phase 4: Checking the screen after a step (1 week)

1. After a click, tap or typing step that is part of a multi-step task, capture the screen: `takeShot()` on Android 11+, `readScreen()` text on Android 8–10 or when the window is protected.
2. Send it at `resolution: "low"` (~440 tokens) through `ScreenImage.toJpeg`. Medium was faster in the probe (1.2 s against 1.8 s), so compare both on the test set before choosing.
3. Questions: `step_succeeded` (yes/no), `blocker` (none, permission, login, error, ad, update), `next` (continue, retry, ask_user, stop).
4. Return the answer in the tool result so Live decides. At most two retries, then say plainly what went wrong.
5. Check whether this beats today's `look_at_screen`, which describes the screen with `GeminiTextClient.describeImage`. If Decisions is cheaper and as accurate, use it for the yes/no part only.

**Done when:** multi-step tasks on test phones (Android 8, 11, 14) finish more often and stuck tasks end within 3 tries.

---

## Phase 5: Cleaner memory (3–4 days, do second)

Off the voice path, so latency does not matter.

1. In `MemoryStore.save()`, after the current word-overlap check, ask in one call about the new note: `worth_saving`, `importance` (score 1–5), `category` (choice), plus `dup_N` and `contradicts_N` for each stored note (note text in the instructions). That is up to ~300 questions for 150 notes.
2. Duplicate: keep one note. Contradiction: replace the old note (the existing `replaces` and `logRemoved` paths already support this). Full: drop the lowest importance first.
3. Run it in the existing coroutine scope after the save returns, not with WorkManager.

**Done when:** no duplicates on the memory test set and fewer low-value notes in real use.

---

## Phase 6: Reranking document search (3–4 days, do first)

1. In `RagSearchEngine.search()`, take the top 20 chunks from `fuseRankings`.
2. Use the query as the input, with one score question per chunk (0 not relevant … 3 answers it directly), chunk text in the instructions.
3. Pass the top 3–4 chunks scoring ≥ 2 to `assembleContext`. If none score ≥ 2, the knowledge-search tool result says nothing relevant was found.
4. This adds ~1.1 s to `knowledge_search`. The tool already runs while Live waits, so it is acceptable if answers clearly improve.

**Done when:** answers on the document part of the test set get better.

---

## Phase 7: Smaller additions (2–3 days)

- **Skill choice:** a `choice` question over `SkillRegistry.all()` names to suggest the right skill in the `skill` tool result.
- **Document tagging:** when `RagDocumentStore` adds a document, one call sets its type and flags sensitive content.

---

## Work that applies to every phase

- **Order:** Phase 1 → 6 → 5 → 3 → 4 → 7 → 2. Do the low-risk, off-voice-path work first; Phase 2 only after measuring.
- **Cost:** these calls use the user's own key. Text checks are ~260 tokens, low screenshots ~440. Add a "lite mode" that skips screen checks.
- **Privacy:** a settings switch and a line in onboarding and the privacy policy saying that screen text and screenshots go to Google for checks. Never send password fields or OTP screens.
- **Testing:** unit tests for parsing and every answer path; rerun the test set when thresholds change; real-phone tests on Android 8, 11 and 14.
- **Rollout:** behind flags, beta users first, read the logs, tune thresholds, then on for everyone.
- **Website:** once features ship, add them to the feature list in `sites/ultron-showcase`.

## Risks

| Risk | How to handle it |
|---|---|
| Beta API changes or is retired | All calls inside `DecisionsClient`, master off switch, fallbacks |
| ~1–2 s per call | Keep calls off the first-reply path; run in parallel with existing work |
| Free-tier limits on users' keys | Cache, skip optional checks after a 429 |
| Safety checks feel like friction | Block only clear mismatches, never add taps |
| Screenshots leaving the phone | Optional, text first, node tree instead of images where possible |

**Rough total:** 4–5 weeks for one developer. Phases 1, 6 and 5 (about 1.5 weeks) are the safest first wins.
