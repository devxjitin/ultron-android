# Long-term memory: plan

Status: **built and merged (phases 1 to 4, PR 47 to 50)**. Written 2026-11-06. Owner: Jitin Kumar Sengar. Tracked in `Product-Manager/PM.md` (Open items, P1).

Progress: **phase 1 merged** (PR 47). **Phase 2 built** (`ConversationHistorySearch`, `DateRange`, PR 48). **Phase 3 built** (tools, connector, prompt, no session-start history, PR 49). **Phase 4 built** (Memory page "Past conversations", Storage line, changelog). Phone test (2026-11-06, OnePlus CPH2699): turns are saved, and in a new session the assistant answered correctly from an earlier conversation. Not yet checked on the phone: the Memory page "Past conversations" list, the Storage line and the delete actions, and the silent-turn retry from PR 46.

Phase 3 as built, where it differs from the sections below:
- The Long-term memory connector is on for fresh installs **and for existing installs** until the user switches it off themselves (`effectiveDeviceTools`: a connector in `ON_UNTIL_CHOSEN_IDS` with no saved choice counts as on).
- With the connector **on**, a new session sends no history. With it **off**, the last few turns are still replayed as before, so nothing regresses. Saving to long-term memory stops while it is off.
- `ConversationStore` (last 12 turns, 30 minutes) is **kept**: it feeds the notification line and the connector-off replay. The migration in 3.7 is dropped because phase 1 already saves every turn.
- Tool results go through `ConversationHistoryTools` (testable) and are marked untrusted. `recent_conversation` skips the session in progress.

## 1. Goal

Ultron should remember **every past conversation** on the phone, not just the last few minutes. A session does not start with old messages loaded. Instead, the assistant gets a **Long-term memory** connector with tools it calls during a conversation to look up what was said before, and then answers from what it found.

Examples it must handle:

- "Kal maine tumse kis cheez ke baare mein poocha tha?"
- "Last week jo restaurant bataya tha uska naam kya tha?"
- "Wo reminder 7 baje ka kar do" (right after reopening the app)
- "Did I ever tell you my car number?"

## 2. How it works today

| Part | Where | What it does |
|---|---|---|
| Recent turns | `data/ConversationStore.kt` (DataStore) | Keeps the last **12** turns, and only those from the last **30 minutes** (`HISTORY_TTL_MS`). Older turns are lost. |
| History seeding | `KnowledgeBaseProvider.liveHistoryTurns`, `GeminiLiveClient.sendHistory` | At every session start, sends up to **10 turns (20 messages)** as `clientContent` with `turnComplete=false`. |
| Memory notes | `data/MemoryStore.kt`, `save_memory` / `forget_memory` | Up to 150 short facts, placed in the system instruction. **Stays as is.** |
| Knowledge search | `RagSearchEngine`, `Bm25Index`, ObjectBox HNSW | Hybrid vector + BM25 search over saved documents. **We reuse this approach.** |

Problems:

- Anything older than 30 minutes is gone.
- Every session sends 20 messages up front: extra tokens on the user's key, a slower start, and old talk the model may wrongly act on.
- There is no way to ask about last week.

## 3. Target design

### 3.1 Storage: every turn, on the phone

New ObjectBox entity (same database as the knowledge base):

```kotlin
@Entity
data class ConversationTurnEntity(
    @Id var id: Long = 0,
    @Index var sessionId: String = "",       // one id per mic session, to keep neighbours together
    var userText: String = "",
    var assistantText: String = "",
    @Index var timestampMillis: Long = 0,
    var embeddingModel: String = "",         // empty = not embedded yet
    @HnswIndex(dimensions = 768, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null
)
```

- New `data/ConversationHistoryStore.kt`: `append`, `recent(count)`, `byDateRange`, `nearest(embedding, limit)`, `delete(id)`, `deleteDay`, `clearAll`, `count`, `sizeBytes`.
- `finalizeTurnIfAny()` in `AssistantViewModel` writes here instead of `ConversationStore`.
- Turns with no user text, or user text shorter than 2 characters (noise), are not saved.
- Kept **forever** until the user deletes them (owner decision needed, see section 6).

### 3.2 Indexing: embeddings in the background

- After a turn is saved, embed `"You: …\nUltron: …"` with `GeminiEmbeddingClient` (`RETRIEVAL_DOCUMENT`, model `gemini-embedding-2`), using the same key rule as knowledge (knowledge key if set, else the Gemini key).
- This must never block the conversation. If it fails (offline, quota), the turn stays with `embedding = null`.
- A backfill pass embeds pending turns when the app opens and after each session, in small batches.
- Turns that are not embedded yet are still found by keyword search.

### 3.3 Search

New `data/ConversationHistorySearch.kt`, built like `RagSearchEngine`:

1. Optional date filter first (`from_date` / `to_date`, phone's time zone).
2. Vector ranking (HNSW, cosine distance up to 0.5) plus keyword ranking, fused with Reciprocal Rank Fusion. Keyword ranking asks ObjectBox which turns contain each query word (case-insensitive, either side of the exchange), weights rarer words higher and ignores a word found in over 30% of turns, so it stays fast with a long history instead of loading every turn into memory like `Bm25Index` does for knowledge.
3. Newer turns win ties.
4. Add the turn just before and just after each match from the same session, so an answer is not cut off.
5. Format, capped at about 4,000 characters:

```
[2026-11-03 18:20] You: kal ka plan kya hai
[2026-11-03 18:20] Ultron: Kal 10 baje dentist appointment hai…
```

If embedding the query fails, fall back to BM25 only instead of failing.

### 3.4 Tools the model can call

Two new tools in a new **Long-term memory** connector:

| Tool | Arguments | Used for |
|---|---|---|
| `search_conversation_history` | `query` (required), `from_date`, `to_date` (YYYY-MM-DD, optional) | "What did I say about X", "kal kya baat hui", "last week" |
| `recent_conversation` | `count` (optional, default 6, max 20) | Picking up right where the last session ended, e.g. "make that 7 o'clock" |

- Added as `DeviceTool` entries so they follow the existing connector, Live declaration and Settings paths (`ConnectorRegistry`, `liveFunctionDeclarations`). `DeviceToolRunner` calls into `ConversationHistoryStore` / `ConversationHistorySearch`.
- Results go out through the existing untrusted-content notice (`UntrustedContent`), because old assistant replies can contain web pages, SMS or notification text.
- The tool label on screen shows "Searching past conversations".

### 3.5 Connector

New entry in `ConnectorRegistry`:

- id `long_term_memory`, name **Long-term memory**, category **Essentials**, kind `PHONE`, no permissions, no special access.
- **On by default** (owner decision needed).
- Turning it off stops both saving new turns and the two tools. Already saved turns stay until cleared.
- Connector page text: stays on this phone; each turn's text is sent to Gemini once to make it searchable; only matching turns are sent when the assistant searches.

### 3.6 Session start: no history

- Stop sending history at session start: remove the `liveHistoryTurns` seeding and `GeminiLiveClient.sendHistory`.
- Add one line to the system instruction when the connector is on: earlier conversations are not in your context; when the user refers to something from before, call `recent_conversation` or `search_conversation_history` first, and never guess.
- Inside one session the Live server already holds the conversation. Reconnects use the resumption handle, as today.
- The silent-turn retry from PR #46 is not affected.

### 3.7 Migration

- On first launch of the new version, copy the turns still in `ConversationStore` into `ConversationHistoryStore`, then remove `ConversationStore` and its DataStore file.
- No other data changes.

### 3.8 App screens

- **Memory page:** a "Past conversations" section with count, storage size, a list by day (newest first) with delete per turn and per day, and **Clear all** (`ui/components/MemoryPage.kt`). There is no admin lock in the app today, so these screens are open like the rest of the Memory page.
- **Storage page:** include the history size.
- `changelog.md`: user-facing entry.

## 4. Work phases

Each phase is its own branch and PR, with tests and a phone test before merge (merge to `main` sends OTA to every phone).

| Phase | What | Main files |
|---|---|---|
| 1 | Entity, store, background embedding and backfill, saving turns from `finalizeTurnIfAny` | `ObjectBoxEntities.kt`, new `ConversationHistoryStore.kt`, `AssistantViewModel.kt` |
| 2 | Hybrid search with date filter and neighbours | new `ConversationHistorySearch.kt`, `Bm25Index.kt`, `RagSearchEngine.kt` (shared helpers) |
| 3 | Two tools, connector, prompt line, remove history seeding, migration | `DeviceTool.kt`, `DeviceToolRunner.kt`, `ConnectorRegistry.kt`, `KnowledgeBaseProvider.kt`, `GeminiLiveClient.kt`, `ConversationStore.kt` (removed) |
| 4 | Memory page section, Storage page, changelog, phone test | `ui/components/*`, `assets/changelog.md` |

## 5. Tests

- Unit: store append/recent/date range/delete/clear; noise turns skipped; backfill picks up pending turns; search ranks an exact name or number first (BM25), finds a paraphrase (vector), respects dates, adds neighbours, stays under the character cap, falls back to BM25 when embedding fails; Hindi (Devanagari) and Hinglish queries; migration copies old turns once.
- Live declarations: tools appear only when the connector is on; no `clientContent` history is sent at setup.
- Phone test: talk, stop, reopen after 1 hour, ask "maine pehle kya poocha tha"; ask about something from yesterday by date; connector off means no tool and no saving; Clear all empties the list.

## 6. Decisions needed from the owner

| # | Question | Recommendation |
|---|---|---|
| 1 | Keep past conversations forever, or delete after N days? | Forever, with Clear all and per-day delete |
| 2 | Long-term memory connector on by default? | On (fits the hands-free goal; nothing leaves the phone except text for search) |
| 3 | Send nothing at session start, or still send the last 1 or 2 turns from the last few minutes? | Nothing; the model calls `recent_conversation` when needed |
| 4 | Embed every turn (one small Gemini call per turn on the user's key), or keyword search only? | Embed; keyword-only misses paraphrases and Hinglish wording |

## 7. Risks

| Risk | Mitigation |
|---|---|
| The model doesn't call the tool when it should | Clear tool descriptions plus the system prompt line; phone-test phrases in Hindi and English |
| Extra delay when it searches (query embedding takes about 0.5 to 1 s) | "Searching past conversations" label; BM25-only fallback |
| Old replies contain outside text (web, SMS) | Results carry the untrusted notice |
| Storage growth | About 3 KB embedding plus text per turn: 10,000 turns is roughly 35 MB; shown on Storage page |
| Embedding quota on the user's key | Background, batched, retried later; never blocks a reply |
| Private conversations on the phone | Stored only on the phone, with Clear all and per-day delete |
