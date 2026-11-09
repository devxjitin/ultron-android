# AssistantViewModel todne ka plan (2026-11-08)

> **Poora ho gaya (2026-11-09):** 1.19.5 se 1.19.10, PR #88 se #93. `AssistantViewModel` ab ~930 lines. Neeche ka plan jaisa tha waisa rakha hai; line numbers purane hain.

`AssistantViewModel.kt` abhi **2,626 lines** ka hai. Isme `_uiState` 163 jagah aur `showToast` 36 jagah use hota hai, isliye har hissa ek hi tarike se bahar nikalega. Har hissa alag chhota PR, ek ke baad ek (REMAINING_WORK.md ka merge niyam: pichhla Publish OTA khatam ho, tab agla merge).

## Sab hisson ka ek hi dhaancha

- Naya class `viewmodel/` package mein, `internal`.
- Constructor mein sirf zaroori cheezein: `scope` (viewModelScope), `state: MutableStateFlow<AssistantUiState>` (wahi `_uiState`), `Application` jab chahiye, aur baaki ViewModel se jo chahiye wo chhote lambdas (`showToast`, `isSessionActive` …). Ek doosre ko seedhe nahi jaante; ViewModel jodta hai.
- ViewModel ke **public `onX()` functions waise hi rehte hain** aur bas naye class ko call karte hain. Isse `AssistantActions`, `AssistantScreen` aur UI mein kuch nahi badlega.
- `AssistantUiState` ke fields ka naam/shape nahi badlega. Pehle sirf code hilana hai, behaviour nahi.
- Har PR: patch version +1, changelog line, `./gradlew test lint assembleRelease` hara. Naye class ke liye jahan ho sake chhote unit tests (fake lambdas se).

## Kram (sabse alag se sabse uljha)

### 1. UpdateManager — 1.19.5 (sabse aasaan, ~130 lines)
Lines ~876–997: `checkForUpdate`, `onUpdateNow`, `prefetchUpdate`, `onInstallUpdate`, `onDismissUpdate` aur unke fields (`lastUpdateCheckMs`, `downloadedUpdate`, `prefetchJob`, `showPrefetchProgress`, `updateCheckInFlight`, `dismissedUpdateVersion`).
- Chahiye: `scope`, `state`, `Application`, `showToast`, `isSessionActive`.
- Dhyan: `onClearCache` (line ~1031) `prefetchJob?.isActive` padhta hai → UpdateManager mein `val isDownloading: Boolean` do.
- `UPDATE_CHECK_INTERVAL_MS` companion se manager mein jaaye.
- Test: check chal raha ho to doosra check na chale; fail hone par `lastUpdateCheckMs` reset ho; quiet check session ke dauran dialog na kholе.

### 2. AudioRouting — 1.19.6 (~200 lines, Android API se bandha, logic kam)
Lines ~2410–2562: `audioFocusChangeListener`, `isPhoneCallMode`, `acquireCommunicationAudio`, `routeToSpeaker`, `releaseCommunicationAudio` aur `previousAudioMode`, `previousSpeakerphoneOn`, `previousVoiceCallVolume`, `raisedVoiceCallVolume`, `audioFocusRequest`.
- Focus badalne par jo karna hai (stop, pause-for-call, mic mute/unmute) wo ViewModel se ek `Listener` interface ke through: `onFocusLost()`, `onTransientLoss()`, `onFocusRegained()`.
- `pauseForCall` / `resumeAfterCall` / `callWatchJob` abhi ViewModel mein hi rahein (session state chhoote hain); step 5 mein session controller mein jayenge.
- Test: phone par hi sahi hoga — call aaye/kate, navigation prompt, headphones. Ye checklist PR mein likhni hai.

### 3. KnowledgeManager — 1.19.7 (~300 lines)
- Lines ~204–222 (`resolveEmbeddingApiKey`, `embeddingClient`, `searchEngine`), ~426–480 (`startEmbeddingMigrationIfNeeded`, `collectKnowledgeDocuments`, `onResetKnowledge`), ~1273–1462 (add/import PDF/update/delete/dismiss error/PDF errors).
- Fields: `ragDocumentStore`, `knowledgeDocMutex`, `knowledgeDocumentsJob`, `embeddingMigrationRunning`.
- Bahar ke liye do cheezein: `suspend fun search(query, trace)` aur `fun hasSearchKey()`. Search retry loop (`SEARCH_RETRY_LIMIT`, `classifySearchFailure`) bhi yahin aaye — tab step 4 ka tool code chhota ho jata hai.
- Dhyan: `savingKnowledgeDocumentTarget` failure par jaan-boojh kar clear nahi hota — waise hi rakhna.
- Test: retry sirf transient errors par, `REINDEX_REQUIRED` par migration shuru, mutex do saves ko overlap na hone de.

### 4. ToolDispatcher — 1.19.8 (~350 lines)
- `onToolCall` ka andar ka hissa (lines ~1731–1820): kaunsa tool kaun chalayega.
- Saath mein: `runMemoryTool`, `runToolSafely`, `toolDisplayName`, `announceTool`, `clearToolLater`, `runPersonalityTool`, `runDeviceTool`, `runHistoryTool`, `ToolBatch`, `pendingToolJobs`, `pendingToolCallIds`, `toolBatches`, `flushToolBatch`, `cancelPendingToolCalls`.
- Shape: `ToolDispatcher.handle(client, calls)` aur `cancel(ids)`. `liveClient === client` wali jaanch ek `isCurrent: (LiveClient) -> Boolean` lambda se.
- Dhyan: duplicate frame par tool do baar na chale; cancel hone ke baad result na bheje; reply watchdog sirf batch ka aakhri result bhejne ke baad shuru ho.
- Test: inhi teen baaton ke unit tests, fake `LiveClient` se.

### 5. VoiceSessionController — 1.19.9+ (sabse bada, ~1,000 lines, sabse risky)
Lines ~1464–2410: `startSession`, `connectLiveSession` (listener), `beginStreaming`, `finalizeTurnIfAny`, `stopSession`, prewarm (`prewarmIfPossible`, `cancelInFlightPrewarm`, `rewarmAfterStop`), go-away, reconnect, idle/turn/speaking/session-limit timers, mic heuristic (`onMicAmplitude`), `pauseForCall`/`resumeAfterCall`, `releaseResources`.
- Ise bhi **do PR** mein karna:
  - **5a:** timers aur mic heuristic (`SessionTimers`: idle, turn, speaking watchdog, session limit, `onMicAmplitude`) — ~250 lines.
  - **5b:** baaki session lifecycle + warm session + reconnect.
- Is waqt tak Update/Audio/Knowledge/Tools alag ho chuke honge, to controller unhe constructor mein lega.
- Test: phone par poori checklist — cold start mic tap, warm session tap, 2 second ke andar tap, interrupt, tool call ke beech interrupt, call aana, app background, go-away, net jaana/aana, 15 min limit.

## Ke baad ViewModel mein kya bachega
UI state, page navigation (`openPage` …), settings/memory/images/history/usage ke chhote handlers, aur managers ko jodna. Andaza ~900–1,100 lines. Agar phir bhi bada lage, `SettingsActions` / `ImagesManager` / `UsageTracker` agla chhota round ho sakta hai (abhi plan mein nahi).

## Kya nahi karna
- Hilate waqt behaviour badalna, rename karna ya "saath mein" bug fix — alag PR.
- Hilt/DI jodna — abhi zaroorat nahi, constructor se kaam chalega.
- Ek PR mein do hisse.

## Shuruaat
`main` se naya branch `refactor/update-manager` (abhi checkout `feat/objectbox-source` par hai, wo purana hai).
