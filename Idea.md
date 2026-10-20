# Idea — White-label Voice Assistant (Master Plan)

> Ye file app ka master plan hai: abhi ke saare issues + unka solution, white-label setup, UI blueprint,
> policy approval, About page, phases aur future improvements.
> Line numbers 2026-10-03 ke code ke hisaab se hain — code badalne par thode khisak sakte hain.
> Kaam shuru karne se pehle har phase ka approval alag se liya jayega.

---

## 0. Final decisions (defaults approved)

| Decision | Choice |
|---|---|
| Branding | **Ek hi APK**, branding runtime par set hogi (naam, logo, color, greeting). Launcher icon/naam sab clients ke liye same. |
| Policy approval | **In-app**: client admin setup ke time Terms + Privacy Policy accept karega; visitor ko notice dikhega. Play Store compliance abhi scope me nahi. |
| About / "Powered by" | Developer ka naam hi rahega: **Jitin Kumar Sengar · devxjitin@gmail.com** (baad me product brand naam se badal sakte hain). |
| Client info | Org name, logo, support contact — sab setup wizard se aayega, code me kuch hardcoded nahi. |

---

## 1. Goal

Ek **independent, white-label voice assistant** jise koi bhi client (hospital, school, office, store, event)
ke liye developer ek device par setup karke de sake:

- Client ka naam, logo, brand color, greeting, assistant naam/voice/language
- Client ki apni knowledge (PDF / text)
- Client admin ne Terms + Privacy Policy accept ki ho, visitors ko clear notice mile
- Visitor ko sirf saaf, simple kiosk screen dikhe; admin cheezein PIN ke peeche
- Professional About page

---

## 2. Abhi ke issues aur unka solution

### 2.1 Bugs

| # | Severity | Kahan | Problem | Solution |
|---|---|---|---|---|
| B1 | 🔴 High | `AssistantViewModel.kt:1297` (`onError`), `onTurnTimedOut`, `onClosed` (no internet) | ERROR state me `conversationStore` clear nahi hota, idle timeout bhi cancel. Agla visitor mic dabaye to cold connect (`:1034`) purane visitor ki history model ko bhej deta hai — **privacy leak**. | Har ERROR transition par `clearHistoryAndRewarm()` jaisa clear chalao (ya ERROR me bhi idle timer chalu rakho jo history clear kare). Ek helper `enterErrorState(message)` banao jo release + clear + state set kare, taaki saare error paths ek jagah se jaayein. |
| B2 | 🔴 High | `AdminPinPrompt.kt:68` → `AssistantViewModel.kt:347` | PBKDF2 (300k iterations) **main thread** par — UI freeze / ANR risk. | `onAdminUnlock` ko suspend/coroutine me `Dispatchers.Default` par chalao; UI me "Verifying…" state dikhao; result `uiState` se aaye. |
| B3 | 🟠 Medium | `AudioPlayer.kt:368` | `clearQueueForInterruption()` ka coroutine released `AudioTrack` par `pause()` chala sakta hai → `IllegalStateException`, scope me handler nahi → crash (plausible race). | `track` read + pause/flush/play sab `trackLock` ke andar; release bhi lock ke andar karo aur release ke baad `track = null` pehle set karo; `runCatching` lagao; scope me `CoroutineExceptionHandler`. |
| B4 | 🟠 Medium | `AssistantViewModel.kt:453` | `launchPersist` failure toast SUCCESS (green ✓) type me. | `AppNotificationType.ERROR` pass karo. |
| B5 | 🟠 Medium | `SettingsDialog.kt:967` | "Remove knowledge API key" dialog kehta hai main key use hogi — code me fallback hai hi nahi. | Text: "Knowledge search and PDF import will stop working until a new key is added." |
| B6 | 🟠 Medium | `MainActivity.kt:87` | PDF sirf file-name extension se pehchana jata hai; Drive/WhatsApp files reject. | `contentResolver.getType(uri) == "application/pdf"` ya `%PDF` magic bytes check; extension sirf fallback. |
| B7 | 🟠 Medium | `AssistantViewModel.kt:714`, `AppSettingsStore.kt:149` | Auto-persona har knowledge change par admin ka manual prompt overwrite karta hai; "Updating" dialog 60s tak block. | Settings me toggle "Auto-update prompt from documents" (default ON, manual edit karte hi OFF); blocking dialog ki jagah non-blocking banner. |
| B8 | 🟡 Low | `AssistantViewModel.kt:785, 842, 892` | `catch (e: Exception)` `CancellationException` bhi pakad leta hai. | Pehle `catch (e: CancellationException) { reset state; throw e }`. |
| B9 | 🟡 Low | `AssistantViewModel.kt:1777, 1786` | `isSpeakerphoneOn` Android 12+ par deprecated; voice-call volume badla jata hai, restore nahi. | API 31+ par `setCommunicationDevice(speaker)` / `clearCommunicationDevice()`; purana volume save karke release par restore. |
| B10 | 🟡 Low | `AssistantViewModel.kt:1328` | Warm session server se band ho to naya prewarm nahi hota. | Warm close/error ke baad (idle + foreground ho to) backoff ke saath `prewarmIfPossible()`. |
| B11 | 🟡 Low | `GeminiTextClient.kt:139` | Sirf `parts[0]` padha jata hai; safety block par `candidates` missing → generic error. | Saare text parts join karo; `promptFeedback.blockReason` check karke clear `UserMessageException`. |
| B12 | 🟡 Low | `ChatHistoryStore.kt:98` | Corrupt decode par empty list → agla append poori history mita deta hai. | Decode fail par exception/flag (knowledge store jaisa) aur admin ko "Reset history" option. |
| B13 | 🟡 Low | `AssistantViewModel.kt:1040` | `!isForeground` par session-start coroutine return karta hai, UI CONNECTING par atak sakti hai. | Early return par state wapas IDLE karo. |

### 2.2 Kiosk security / privacy

| # | Issue | Solution |
|---|---|---|
| S1 | History, Knowledge aur General settings **bina PIN** ke khule — visitor dusron ki chats padh/knowledge delete kar sakta hai. | Visitor screen se admin tabs hatao; hidden admin entry (logo long-press 3s) + PIN; admin area 2 min inactivity par auto-lock. (Phase 3) |
| S2 | PIN har install par same; salt+hash APK me — offline brute-force (10^6) aasaan. | **Per-install PIN** setup wizard me banega, hash EncryptedSharedPreferences me. "Change PIN" Device settings me. (Phase 1) |
| S3 | API key device/APK me. | Short term: theek (encrypted storage). Future: chhota backend jo Gemini Live **ephemeral tokens** de. |
| S4 | Expiry device clock par — time peeche karke bypass. | Future: server/NTP time check; tab tak "monotonic + last-seen time" guard (clock peeche jaye to expired maano). |
| S5 | Chat history hamesha ke liye store hoti hai. | Retention setting: Off / 1 / 7 / 30 din; auto-delete on app start + daily. (Phase 2) |

### 2.3 Performance / architecture

| # | Issue | Solution |
|---|---|---|
| P1 | Mic amplitude 25×/sec `_uiState` update → poori `AssistantScreen` recompose. | Amplitude ke liye alag `StateFlow<Float>`; `VoiceRibbon` use draw phase me padhe. |
| P2 | Har client apna `OkHttpClient` banata hai (Live, Text, Embedding). | Ek shared `HttpClients` singleton; per-use timeouts `newBuilder()` se. |
| P3 | Saari knowledge (docs + chunks + embeddings) ek Preferences DataStore string me; har edit par full rewrite + ObjectBox ka full rebuild (`KnowledgeIndexConsistency.kt:109`). | ObjectBox ko source of truth banao; add/update/delete incremental; DataStore sirf legacy migration ke liye. |
| P4 | Chat history (50×200 turns) ek JSON string; har turn par full rewrite. | Room (ya ObjectBox) table: sessions + turns. |
| P5 | `AssistantViewModel` ~1850 lines; MainActivity se 40+ lambdas. | `SessionController`, `KnowledgeController`, `AudioRouteManager`, `AdminController` me todo; UI events ke liye `sealed interface UiAction` + ek `onAction()`. |
| P6 | `AppForegroundService` kuch kaam nahi karta (`specialUse`, `START_STICKY`) — Play policy risk. | Hatao; ya sirf kiosk-mode me real kaam (e.g. watchdog) ke liye rakho. |
| P7 | Release build minify ON par `proguard-rules.pro` khaali; release build verify nahi hua. | Release build + device par smoke test; zaroorat ho to ObjectBox/OkHttp rules. |

### 2.4 Dead code / cleanup

| Item | Action |
|---|---|
| 7 callbacks — `onSaveApiKey`, `onSaveEmbeddingApiKey`, `onIdentitySaved`, `onSystemPromptChanged`, `onResetSystemPromptToDefault`, `onDocumentFormatPromptChanged`, `onRetrievalChunkLimitSelected` — `SettingsPage` me declare hain par kabhi call nahi hote (`SettingsDialog.kt:91-103`) | ViewModel functions, `AssistantScreen`/`MainActivity` wiring aur `AppSettingsStore` ke `setIdentity`, `setSystemPrompt`, `resetSystemPromptToDefault`, `setDocumentFormatPrompt`, `setRetrievalChunkLimit` hatao (~150 lines). |
| `runIfAuthorized` (sirf tests me) | Hatao + uska test. |
| `Color.kt`: `StateIdle…StateError`, `AssistantGlowTeal/Gold`, `AssistantBackgroundDeep` | Hatao (Phase 3 me dynamic theme aayega). |
| `GeminiTextClient.DOCUMENT_FORMAT_MODEL` default | Hatao, `AssistantOptions.DATASET_MODEL` hi use ho. |
| `MicButton(accent = false)` branch | Simplify. |
| Purane naam: `onUploadMarkdownFile`, `markdownPickerLauncher`, `onMarkdownFileUploadFailed` | `onUploadPdf`, `pdfPickerLauncher`, `onPdfUploadFailed`. |
| File names: `TranscriptDialog.kt` (HistoryPage), `SettingsDialog.kt` (SettingsPage) | `HistoryPage.kt`, `SettingsPage.kt`. |
| Purane comments: `Color.kt:11` (school logo), `ConversationStore` ("survives restart"), ViewModel `:1421` ("45s idle"), `KnowledgeBaseProvider` doc | Sahi karo. |
| Hardcoded About (`SettingsDialog.kt:814-831`) | Naya `AboutPage` (Phase 4). |

---

## 3. White-label architecture

### 3.1 Kya-kya configurable hoga (runtime, ek APK)

| Group | Fields |
|---|---|
| Organization | `orgName`, `logo` (internal storage me copy), `brandColor` (8 presets), `supportPhone`, `supportEmail`, `policyUrl` (optional, QR ke liye) |
| Assistant | `assistantName`, `greeting`, `voice`, `language`, `systemPrompt`, `autoPersona` toggle, `micSensitivity`, `bargeIn` |
| Connections | Live API key, Knowledge API key (dono "Test connection" ke saath) |
| Privacy & Data | `historyRetentionDays` (0/1/7/30), `visitorNoticeText` |
| Device | admin PIN, expiry, `installId` (support ke liye), config export/import |
| Policy | `acceptedPolicyVersion`, `acceptedAtMillis`, `acceptedByName` |
| State | `setupComplete` |

### 3.2 Hardcoded cheezein jo hatani/generic karni hain

- `DEFAULT_SYSTEM_PROMPT` me "You are Assistant" → `{assistantName}`/`{orgName}` placeholders.
- Notification text "Assistant is ready" → `"{assistantName} is ready"`.
- About page ka personal hardcoded text → `AboutPage` (branding + "Powered by").
- `Color.kt` fixed palette → `brandColor` se generated `ColorScheme`.

### 3.3 Config export/import

- Ek client ke kai devices: admin **Export config** → JSON file (branding, assistant, privacy settings; **API keys aur PIN kabhi nahi**).
- Naye device par wizard ke pehle step me **Import config** → baaki steps pre-filled.
- Future: knowledge bhi export (docs + embeddings) taaki dobara embed na karna pade.

---

## 4. First-run Setup Wizard

App pehli baar khule (ya `setupComplete == false`) to visitor screen ki jagah wizard:

| Step | Screen | Validation |
|---|---|---|
| 1 | **Welcome + Terms & Privacy Policy** — dono scroll karke padho, admin ka naam, "I agree" checkbox | Checkbox + naam zaroori |
| 2 | **Admin PIN banao** (6 digit, confirm) | Dono match; 000000/123456 jaise weak PIN reject |
| 3 | **Organization** — naam, logo, brand color, support contact | Naam zaroori; baaki optional |
| 4 | **Assistant** — naam, greeting, voice (preview), language | Defaults pre-filled |
| 5 | **API keys** — Live + Knowledge, har ek ka "Test connection" | Live key test pass zaroori; Knowledge key skip ho sakti hai (warning ke saath) |
| 6 | **Knowledge** — PDF upload / text add | Optional, skip |
| 7 | **Privacy** — history retention, visitor notice text | Defaults: 7 din, standard notice |
| ✔ | **Preview** (visitor screen jaisa dikhega) → **Launch** | `setupComplete = true` |

Rules:
- Wizard beech me band ho to progress save rahe, wahi se resume.
- **App lock nahi hogi** (pichhli baar setup gate revert hua tha): setup pending ho to visitor screen par "Setup pending — please contact staff" + hidden admin entry se wizard resume.
- Purane installs (migration): existing API key mili → `setupComplete = true` maan lo, sirf policy accept + naya PIN set karwana hai.

---

## 5. Policy approval

### 5.1 Client / admin
- Setup step 1 me **Terms of Use + Privacy Policy** accept karna zaroori.
- Save: `acceptedPolicyVersion`, date-time, admin ka naam → About page par dikhega.
- App update me policy version badle → agli admin login par dobara accept (tab tak admin area locked, visitor side chalti rahegi).

### 5.2 Visitor
- Home par permanent **footer notice**: "🔒 AI assistant · Personal info share na karein · Policy".
- Footer tap → short policy summary sheet + QR code (`policyUrl` set ho to).
- Notice text client edit kar sakta hai (Privacy & Data me).

### 5.3 Policy content (template, `assets/policy/`)
- `terms_v1.md`, `privacy_v1.md` — `{orgName}`, `{supportEmail}`, `{retentionDays}` placeholders auto-fill.
- Privacy policy me clear likha ho:
  - Awaaz aur text Google Gemini API ko process ke liye jaata hai
  - Device par kya store hota hai (chat history, kitne din), kaise delete hota hai
  - Mic sirf session ke time on rehta hai
  - Personal / sensitive info share na karein
  - Contact for queries/deletion
  - India **DPDP Act 2023** ke hisaab se notice
- ⚠️ Template ka final text lawyer/client se review karwana zaroori — ye legal advice nahi hai.
- ⚠️ **Gemini API terms check**: Gemini API Additional Terms me under-18 users se related restriction hai. School / bachchon wali deployment se pehle Google ke current terms verify karo.

---

## 6. UI blueprint — kya, kahan, kab

### 6.1 Visitor Home (kiosk) — sirf ek kaam: baat karna

```
┌──────────────────────────────────────────┐
│ [Client Logo] Org Name          ● Online │  ← top bar (hamesha)
│                                          │
│         Namaste! Main Riya hoon.         │  ← idle: greeting
│        Aap mujhse kuch bhi poochein      │     active: live captions
│                                          │
│      ～～～～ voice ribbon ～～～～          │  ← state color ke saath
│                                          │
│              Listening…                  │  ← status line
│                                          │
│                 ( 🎤 )                   │  ← BADA mic button (center)
│                [ 🔇 ]                    │  ← mute (sirf session ke time)
│                                          │
│ 🔒 AI assistant · Personal info share na │  ← privacy footer (hamesha)
│    karein · Policy [QR]                  │
└──────────────────────────────────────────┘
   Admin entry: logo par 3 sec long-press → PIN
```

| Element | Kahan | Kab dikhe |
|---|---|---|
| Logo + Org name | Top-left | Hamesha |
| Online/Offline dot | Top-right | Hamesha (offline = red + "No internet") |
| Greeting | Center | Idle |
| Live captions (user / assistant) | Center, 2-3 lines, auto-scroll | Listening / Speaking |
| Voice ribbon | Center | Hamesha (idle me slow) |
| Status line | Ribbon ke neeche | Har state me (table neeche) |
| Mic button (bada, ~96dp) | Bottom-center | Hamesha (expired me hidden) |
| Mute button | Mic ke neeche | Sirf active session me |
| "1 min bacha hai" banner | Top bar ke neeche | Session cap se 60s pehle |
| Privacy footer | Bottom | Hamesha |
| Bottom nav (Knowledge/History/Settings) | — | **Visitor ko kabhi nahi** |

### 6.2 State table

| State | Center | Status line | Mic button |
|---|---|---|---|
| Idle | Greeting + "Tap to talk" | — | Brand color, halki pulse |
| Connecting | "Ek pal…" | spinner | Amber, tap = cancel |
| Listening | Live caption (user) | Listening… | Pulse ring + Stop |
| Thinking | Caption freeze | Thinking… | Stop |
| Speaking | Live caption (assistant) | — | Stop |
| Session ending | "1 min bacha hai" banner | — | — |
| Error / Offline | Friendly message + "Dobara try karein" | red | Retry |
| Setup pending | "Setup pending — please contact staff" | — | Disabled |
| Expired | Full screen "Service inactive" | — | Hidden |
| Idle 2+ min | **Attract mode**: dim screen, "Tap to ask me anything" | — | — |

### 6.3 Admin area (PIN ke baad)

```
┌ Admin ───────────────────────────── [Lock] ┐
│ Knowledge │ History │ Settings │ About     │  ← top tabs
├────────────────────────────────────────────┤
│ Settings sub-sections:                     │
│  • Branding     (org name, logo, color,    │
│                  greeting, assistant name) │
│  • Assistant    (voice, language, prompt,  │
│                  mic, barge-in)            │
│  • Connections  (API keys + Test button)   │
│  • Privacy&Data (history retention,        │
│                  visitor notice text)      │
│  • Device       (expiry, PIN change,       │
│                  export/import config)     │
└────────────────────────────────────────────┘
```

- Admin area 2 min inactivity / app background par auto-lock → visitor home.
- Active voice session admin kholte hi band (abhi jaisa).
- Har section ka apna **Save** (ek giant Developer form nahi); save ke baad app restart nahi — sirf warm session invalidate.
- Knowledge list: title, source (PDF/Text), chunk count, last updated, search box; PDF docs ka preview pehle, edit baad me.
- History: search, date-wise grouping, retention info.

### 6.4 Professional About page

```
        [Client Logo]
      Riya — Virtual Assistant
      for ABC Hospital

  ─────────────────────────────
  Version 1.0.0 (build 2)
  Device ID   A3F9-21C7        (support ke liye)
  Knowledge   12 documents · updated 2 Oct
  Policy      Accepted v1.0 on 3 Oct 2026 by <Admin>

  Privacy Policy              ›
  Terms of Use                ›
  Open-source licenses        ›   (Inter font OFL, OkHttp, ObjectBox, AndroidX)
  Contact support             ›   (client ka contact)
  ─────────────────────────────
  Powered by Jitin Kumar Sengar · devxjitin@gmail.com
  © 2026
```

### 6.5 Look & feel rules
- Dark kiosk theme rahega; accent = client ka `brandColor` (ribbon gradient, mic, selected tabs).
- Typography: Inter; visitor text bada (greeting 30–36sp, captions 20–24sp) — 1–2 meter door se padha ja sake.
- Touch targets ≥ 48dp; mic ~96dp.
- Accessibility: status line par `liveRegion`, ribbon/mic ka `contentDescription`, contrast check.
- Visitor strings English + Hindi (language setting ke saath badlein); admin UI English.

---

## 7. Implementation phases

> Har phase: plan approval → code → build + unit tests → device test → `track.md` entry → alag commit.

### Phase 0 — Quick fixes + cleanup (chhote, safe)
- B1, B2, B4, B5, B6, B8, B13 + section 2.4 ka dead code.
- Files: `AssistantViewModel.kt`, `AdminPinPrompt.kt`, `SettingsDialog.kt`, `MainActivity.kt`, `AssistantScreen.kt`, `AppSettingsStore.kt`, `GeminiTextClient.kt`, `Color.kt`, `AdminAccessController.kt` (+ test).

### Phase 1 — White-label foundation + Setup wizard
- Branding/privacy/policy/setup fields (`AppSettingsStore.kt` ya naya `BrandingStore.kt`).
- Per-install PIN (`AdminPinStore.kt`) + migration.
- (naya) `ui/setup/SetupWizard.kt`, logo picker, "Test connection".
- Config export/import.
- Hardcoded prompt/notification text generic.

### Phase 2 — Policy + privacy
- (naya) `assets/policy/terms_v1.md`, `privacy_v1.md`, `ui/policy/PolicyScreen.kt`.
- Acceptance tracking + version bump re-accept.
- Visitor footer notice + summary sheet + QR.
- History retention auto-delete.

### Phase 3 — UI restructure
- Visitor home redesign (captions, bada mic, footer, attract mode, session-ending banner).
- Hidden admin entry + admin shell (tabs, sub-sections, per-section save, auto-lock).
- `brandColor` se dynamic theme; P1 (amplitude StateFlow).
- B7 (auto-persona toggle + banner).

### Phase 4 — About + polish
- (naya) `ui/about/AboutPage.kt`, open-source licenses screen.
- Hindi visitor strings, accessibility fixes.

### Phase 5 — Stability / architecture (optional, baad me)
- B3, B9, B10, B11, B12; P2, P3, P4, P5, P6, P7.

---

## 8. Future improvements

| Area | Idea |
|---|---|
| Hands-free | Wake word ("Hey {assistantName}"); camera/proximity se visitor paas aate hi greet |
| Kiosk mode | `startLockTask` / device-owner mode — visitor app se bahar na ja sake; boot par auto-start |
| Backend | Ephemeral tokens (API key device par nahi); remote config + knowledge sync; ek dashboard se kai devices |
| Analytics | Top questions, unanswered (`no_match`) questions, har answer par 👍/👎, peak hours |
| Tools (function calling) | "Staff ko bulao" (call/SMS/webhook), appointment/booking, QR handoff (jawab phone par) |
| Visual answers | Knowledge se image/map/table screen par dikhana |
| Knowledge | DOCX/TXT/website URL import; knowledge backup export/import; document versioning |
| Offline | Net na ho to top FAQs ke saved text answers |
| Multi-language | Auto language detect; visitor UI har supported language me |
| Text input | Bolne me dikkat ho to keyboard se sawal |
| Quality | Crashlytics/Sentry, ViewModel unit tests, CI (build + tests har push par), release build pipeline |
| Updates | In-app update check / OTA (Play ya self-hosted) |
| Security | Encrypted chat history at rest; tamper-proof expiry (server time) |
| Per-client APK (optional) | Gradle build flavors — client ka apna launcher icon/naam, agar kabhi chahiye |
| Play Store | Privacy policy URL, Data Safety form, mic permission justification, FGS review |

---

## 9. Risks / dhyan ki baat

- Purane installs: setup flag missing → migration (keys mili to setup done; policy accept + naya PIN baaki).
- Hardcoded PIN hatne ke baad test devices par naya PIN set karna padega.
- Setup gate pehle revert hua tha — is baar app kabhi "lock" nahi hogi, sirf "Setup pending" message.
- Policy text legal review ke bina production me na jaye.
- Gemini API terms (age restriction, data use on free tier) deployment se pehle verify.

**Rollback:** har phase alag commit → `git revert <commit>`.

---

## 10. Progress tracker

- [ ] Phase 0 — Quick fixes + cleanup
- [ ] Phase 1 — White-label foundation + Setup wizard
- [ ] Phase 2 — Policy + privacy
- [ ] Phase 3 — UI restructure
- [ ] Phase 4 — About + polish
- [ ] Phase 5 — Stability / architecture
