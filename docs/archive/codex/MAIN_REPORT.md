# Ultron: kya hona chahiye, kya nahi

6 November 2026 · Product recommendations grounded in current repository review.

## Mera verdict

**Ultron ko ek dependable Hindi/Hinglish Android action assistant banana chahiye:** user ki baat samjhe, phone ka useful kaam kare, apni limits clear bataye, aur important action ka reliable result de. Abhi voice, tools, memory, RAG aur updates ka base strong enough hai ki ise discard/rewrite karna waste hoga. Lekin feature count badhane se pehle first-run success, permissions, safe actions aur lifecycle reliability improve karni chahiye.

Primary audience ke liye mera proposed starting assumption: Android power users jo voice se daily phone tasks karna chahte hain, Hindi/Hinglish mein comfortable hain, aur permissions/BYOK setup accept kar sakte hain. Nontechnical mass-market positioning current Developer-settings key requirement ke saath fit nahi hoti. Yeh market research ka result nahi; current product constraints se recommendation hai.

## Existing features: keep / improve / make optional

| Feature | Decision | Reason / change |
|---|---|---|
| Live voice + interruption | Keep, highest priority | Core identity. Fast stop, reconnect, audio-route handling and honest state labels improve karo. |
| Hindi/Hinglish and regional language options | Keep | Voice UX ka meaningful differentiator; mixed-language task evaluation chahiye. Language count alone quality proof nahi. |
| Device tools | Keep, curate | Common actions first. Har action ke inputs, risk, permissions and verifiable outcome define karo. |
| Accessibility screen control | Keep as explicit advanced capability | Powerful but fragile. Current-app boundary, stale-screen recheck, step limit, sensitive-screen pause. |
| Local memory | Keep | Existing edit/delete/restore valuable. Source, last updated, expiry and consent visibility add karo. |
| Knowledge base | Keep | Personal utility; processing progress, source-open links and cloud-processing disclosure improve karo. |
| Long-term conversation history | Optional | Retention control, delete/export and visible search scope. Private mode should exclude new sessions from persistence. |
| Web search / reader | Keep | Useful grounding; source display and failure state clear rakho. External content ko instructions na banne do. |
| YouTube search | Optional connector | Useful but secondary to daily phone tasks; provider blocking ko honest error banao. |
| YouTube downloads | Optional, low roadmap priority | Separate media workflow maintenance load lata hai. Core activation/voice experience is par depend na kare. |
| Reminders/calendar | Keep, strengthen | High-frequency utility; time, timezone, recurrence and delivery status reliable hone chahiye. |
| Skills | Keep advanced | Bundled reviewed skills useful; arbitrary imported instructions ko higher trust/permissions na milen. |
| BYOK | Keep as explicit product choice | Provider charges/quotas belong to user's key. Setup ko Developer menu mein hide na karo. |
| Activation-key licensing | Keep for current distribution if desired | Fix races/recovery first; license entitlement and AI usage billing distinct products hain. |
| Usage and storage pages | Keep | Explain what metrics actually cover; local duration is not full provider billing. |
| OTA | Keep | Dedicated production signing, beta/stable promotion, compatibility checks and recovery needed. |

## Features main ab add karta

### 1. Setup assistant and readiness page — P1

Flow: language → choose BYOK → explain app license vs provider usage → validate key/model access with explicit small test → request mic when starting first task → show one successful example. Other permissions just in time. Basic readiness statuses: account, activation, API key, mic, selected connector, network.

Success: user can diagnose 'why not working' without Developer menu/logcat. No live API validation was performed in this audit. Avoid automatic expensive test calls.

### 2. Action preview, receipts and undo — P1

Read-only question directly answer. Reversible local action can execute with visible receipt. External-send, deletion, sensitive settings or ambiguous target gets exact preview: **kya, kahan, kis par**. Confirmation should bind to those exact arguments and expire on change. Hands-free voice confirmation can preserve convenience for suitable tasks; private/sensitive actions need device-unlock/trusted UI.

Receipt example: 'Calendar event “Dentist”, 8 Nov, 11:00 — cancelled', with undo only if actual restore is supported. Unknown remote outcome ko 'done' mat bolo. Stop button cancels future steps; already committed external action ka undo promise mat do.

Success: every side effect has operation ID and observable outcome; duplicate tool frame/retry doesn't repeat action.

### 3. Permission continuation — P1

User Allow kare to still-valid pending task continue ho; Deny kare to clear limitation and alternative. Permanent denial tabhi bolo jab actual state supports it. Background mein task waiting status and return-to-app action do.

Success: grant/deny/reopen tests pass; one request produces at most one side effect.

### 4. Privacy centre and private sessions — P1/P2

One place: microphone destination, selected tool context sent to model, PDF/embedding cloud processing, local stores, telemetry and retention. 'Stored locally' and 'processed remotely' alag explain karo. Private session disables new history/memory persistence; it does not claim local-only AI processing. Export and delete separate controls; deleting data should invalidate derived embeddings/index entries too.

Success: user can see each data category, destination and deletion effect. Key/token/audio excluded from diagnostic export by default.

### 5. Task list and reliable reminder lifecycle — P2

Upcoming, completed, failed, paused and legacy automations visible. Per-task disable/cancel. Daily-time vs every-24-hours distinction. Reminder delivery exactness visible; recovery after reboot and timezone change tested.

Success: no invisible legacy rule keeps surprising user; missed run has deliberate catch-up/skip policy.

### 6. Source-first knowledge answers — P2

Answer with document/section reference and one-tap original source, processing status, last indexed time and 'not found' distinction. For factual personal-document queries, retrieved evidence should support response; low-confidence absence shouldn't be turned into a fabricated answer.

Success: curated Hinglish document questions measure correct-source coverage and unsupported-answer rate. Existing source labels retained and improved.

### 7. Connector health and diagnostics — P2

Connected / permission missing / auth expired / service down / temporarily blocked states. Privacy-safe export containing app version, operation IDs, error category and timing—not secrets or raw transcripts.

Success: support can distinguish invalid key, provider quota, network outage and tool permission failure without full personal logs.

### 8. Text fallback for voice tasks — P2

Quiet place, noisy mic or accessibility need mein user same tasks type karke initiate kar sake. Same policy/executor use ho; separate second agent implementation nahi. Current actual typed-input UX needs verification before scope; README's typing claim alone proof nahi.

Success: voice and text requests generate equivalent authorization and outcome behavior.

## Jo main nahi daalta, ya abhi defer karta

| Idea | Decision | Reason / revisit condition |
|---|---|---|
| Always-listening cloud microphone by default | Do not add | Privacy/battery/trust cost. Optional local wake-word later only after measured demand, visible indicator and easy off. |
| Autonomous payments, purchases or message sending from external instructions | Do not allow | User intent and exact action authorization must stay separate from retrieved content. |
| Unlimited all-app screen control | Do not ship as default | Start with user-selected apps/tasks; sensitive surfaces need stricter policy. |
| Silent harvesting of notifications/screens into memory | Do not add | Memory should be inspectable and intentional; collection isn't automatic consent. |
| Arbitrary executable skill/plugin marketplace | Defer | Sandboxing, permissions, signatures, review, revocation and support not yet designed. |
| Many new cloud connectors at once | Defer | Each adds auth/token/error/support burden. Add only against measured repeat usage. |
| Multi-agent orchestration as user-visible selling point | Defer | Current task reliability matters more than agent count; no evidence it is necessary. |
| Full offline LLM promise | Do not promise now | Target-phone capability/model quality/battery measurements absent. Offline deterministic utilities are feasible separately. |
| Complete backend rewrite / Kubernetes | Do not do now | Existing small backend aligns with current scope. Fix contracts and operations first. |
| Remote editable master prompts with unrestricted publishing | Defer | Versioning, review, compatibility, rollback and privilege limits needed before remote behavior control. |
| Replace local memory with mandatory cloud sync | Do not do by default | Optional encrypted sync later if users need it and recovery/conflict design is ready. |
| Remove activation and force subscription migration immediately | Defer | Business decision needs customer expectations/support plan; audit doesn't justify forced migration. |

## Release plan, in practical order

**Milestone A — trust and correctness:** fix B01/B04 license races and B03 wrapper; implement risk policy for high-impact tools; clarify BYOK costs/data; signing transition design. Gate: deterministic race tests and action authorization tests pass. Scope signing migration carefully to avoid breaking existing installs/data.

**Milestone B — daily reliability:** permission continuation, reminder recovery, bounded/cancellable updater, visible task receipts. Gate: device test matrix covers lock/unlock, process recreation, network drop, deny/grant, repeated commands and app update.

**Milestone C — maintainable core:** session coordinator + tool executor + repository boundaries; keep existing stores/adapters. Gate: same behavior through contract tests, reduced ViewModel responsibilities and no OTA smoke regression.

**Milestone D — measured growth:** text fallback, improved citations, optional routines/connector additions based on real task demand. Gate: better task completion with acceptable privacy, cost and support load.

Effort estimates intentionally omitted: device matrix, available developers and rollout/customer constraints unknown. Milestones are sequencing, not promises of delivery dates.

## Metrics I would use

- First useful task completion rate, and which setup step loses users.
- Task success based on observed outcome, not whether model said 'done'.
- Unintended/duplicate high-impact actions: release target zero in evaluated suite.
- Permission grant-to-completion rate; no permanent-denial false messaging.
- Cold/warm first audio p50/p95, stop latency, recovery success, battery during active/idle periods.
- Reminder delivery/cancellation correctness across reboot/timezone scenarios.
- Knowledge source correctness and unsupported claims, including Hindi/Hinglish questions.
- Crash-free sessions and support incidents with privacy-safe telemetry only.

No current retention, revenue, latency or adoption number was available; none invented. Product priorities are engineering judgment based on source review, with wider-launch readiness conditional on runtime evidence.
