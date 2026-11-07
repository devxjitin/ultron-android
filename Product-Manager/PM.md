# Ultron: Product Manager file

Ye file PM ke kaam ka single reference hai. Client: **Jitin Kumar Sengar** (owner). PM: Claude. Last updated: **2026-11-06**, version **1.6.0** on the phone, `main` at merge commit `dd072c1` (PR #46).

Is file me jo likha hai wo sirf wahi hai jo repo, AWS inventory aur client ke faislon se verify hua. Jo verify nahi hua uska saath me likha hai.

---

## 1. Product ek line me

Ultron ek Android voice assistant hai: mic dabao, Gemini Live se baat karo, aur assistant phone ko **hands-free** chalata hai (calls, SMS, alarms, calendar, notifications, screen control). User apni Gemini API key daalta hai. Paid activation key se access milta hai, APK sirf website se aati hai (Play Store nahi).

**Goal jo sab faisle chalata hai: hands-free smartphone control.** Isliye extra permission ya confirm taps nahi jodte.

---

## 2. Kaam karne ke rules (PM aur client)

- Client faisle leta hai, PM options aur recommendation deta hai. Faisla hone ke baad "Decisions log" (section 3) me jodta hai.
- Har kaam: branch, commits, tests, PR, phone test, phir merge. **`main` me merge hote hi OTA build sab phones par jata hai.**
- Har build ke baad: `./gradlew testDebugUnitTest lintDebug assembleDebug` (JDK 17, Android SDK `C:\Users\devxj\AppData\Local\Android\Sdk`). Backend tests: `python -m unittest discover -s mcp/tests` (Python 3.14, `X:\tmp\kira-py314`).
- AWS ya GitHub par kuch delete karne ya push/merge karne se pehle client ki "haan". Backup pehle.
- Phone par test PM nahi kar sakta, client karta hai.
- `track.md` me har badlav ki entry, `android/app/src/main/assets/changelog.md` me user-facing entry (isse hi app ka version aur update notes bante hain).

---

## 3. Decisions log

| Date | Faisla | Kyun |
|---|---|---|
| 2026-11-05 | Google Workspace backend poora hata diya (repo aur AWS) | App me kabhi wire nahi hua tha |
| 2026-11-05 | Gemini ke liye sirf **user ki apni key**; Firebase AI Logic hamesha ke liye hata | Owner ke project se Gemini bill nahi banega |
| 2026-11-05 | Knowledge key optional: hai to wahi, nahi to Gemini key | Ek hi key se kaam chale |
| 2026-11-05 | Server-side AI entitlement nahi; app-side access gate kaafi | User apni key se use karta hai, track karne ki zaroorat nahi |
| 2026-11-05 | Paid activation keys; owner apna store banayega, key email par jayegi | Razorpay in-app subscription drop |
| 2026-11-05 | Distribution sirf website APK | Play policy ke bina SMS, call, accessibility rakh sakte hain |
| 2026-11-05 | Infra: AWS SAM, ek template, `dev` aur `prod` stack (recommendation, abhi implement nahi) | Chhota backend, Lambda free tier, 3 haath se deploy hone wale templates se bachna |
| 2026-11-05 | Remote prompt/skills console, Remote Config hata diye | Sab APK me bundled |
| 2026-11-05 | AWS leftovers delete (3 Lambdas, roles, logs, `ultron-subscriptions`, Razorpay SSM) | Purane, unused |
| 2026-11-05 | Prompts app me nahi dikhenge, built-in chalenge | Owner ka faisla |
| 2026-11-05 | "Read out notifications" Settings toggle, default off | Notification text private hota hai aur Gemini ko jata hai |
| 2026-11-05 | Connectors 6 categories me, ek `ConnectorRegistry` | Data 4 jagah bikhra tha |
| 2026-11-05 | Version aur changelog ek file se (`changelog.md`) | App, About aur update notes mismatch na hon |
| 2026-11-05 | **ActionGuard (tap-to-allow) aur WhatsApp skill ka confirm rule hata diya** | Hands-free goal; extra permission nahi chahiye |
| 2026-11-05 | `tap_position`, `swipe`, `long_press` rakhe | Hands-free goal |

---

## 4. Abhi kya hai (v1.1.0)

**App:** Gemini Live voice (interrupt, 13 languages, 30 voices), ~97 device tools, memory (150 notes), knowledge base (PDF/text, embeddings + BM25), connectors, usage/storage pages, About me version aur What's new, one-tap OTA update.

**Connector categories** (`data/ConnectorRegistry.kt`):

| Category | Connectors | Default |
|---|---|---|
| Essentials | Device info, Device controls, Alarms & reminders, Apps & settings, Weather | On |
| Search & media | DuckDuckGo, Web pages, YouTube Search, YouTube Downloads | DuckDuckGo, Web pages on |
| Personal data | Calendar, Contacts, Location, Files, App usage, Notifications | Off |
| Communication | Calls, Messages | Off |
| Phone control (advanced, warning) | Phone navigation, Screen control | Off |
| Accounts | GitHub (read-only) | Off |

**Backend (AWS us-east-1):** API `api.devxjitin.dev` (Firebase JWT authorizer, App Check). Lambdas: `ultron-api-license`, `-license-admin`, `-releases`, `-web-extract`, `-youtube-search`. Table `ultron-licenses`. Stacks `ultron-api`, `ultron-youtube`. S3 bucket `ultron-app-updates-143920986615`.

**Admin website:** `ultronapp/` (Firebase Hosting) se license keys banana, dekhna, edit, revoke, extend.

**Safety jo bachi hai:** SMS limit (10 per hour, 480 characters), outside text par "untrusted" warning line, default-off risky connectors, encrypted key storage, App Check on API.

---

## 5. Open items (priority order)

**P0, paid distribution se pehle**
1. **Phone acceptance test** v1.1.0 par: activation, key daalna, voice, PDF + search, SMS aur call bina dialog, About What's new, Settings toggle, notifications read-out. Ye client karega.
2. **Customer store aur checkout** (owner banayega): website, download page, privacy policy, terms. Alag discussion baaki hai.
3. **Paid order se key issuance**: abhi keys manually admin site se banti hain.
4. **Release signing**: OTA abhi debug key se sign hota hai. Real release key par jane me existing phones ko ek baar reinstall.
5. **Prompt-injection risk ko customers ko batana**: ActionGuard hatne ke baad outside text se SMS ya call trigger ho sakta hai. Terms/privacy me likhna.

**P1**
- **Long-term memory (planned, not built)**: har purani baat phone par save ho, session start par 20 messages na bheje jayein, aur assistant ek **Long-term memory** connector ke tools (`search_conversation_history`, `recent_conversation`) se zarurat par purani baatein dhoondh kar jawab de. Poora plan: [`docs/LONG_TERM_MEMORY_PLAN.md`](../docs/LONG_TERM_MEMORY_PLAN.md). Pehle owner ke 4 faisle chahiye (section 6): forever rakhna, connector default on, start par kuch na bhejna, har turn ka embedding.
6. **License expiry**: app khuli rahe to expiry/revoke sirf startup par check hota hai. Scheduled refresh chahiye.
7. **Infra ko SAM par laana** (ek template, dev + prod). Abhi 3 CloudFormation templates haath se deploy hote hain.
8. **Live Lambdas Python 3.12 par hain**; repo aur templates 3.14 par. Redeploy baaki.
9. **Release safeguards**: `main` branch protection aur OTA ke liye production environment gate (maine GitHub settings verify nahi kiye). Merge ke time PR #36 `BLOCKED` dikha, kaaran nahi pata.
10. **Firebase console**: AI Logic ka App Check enforcement aur API restriction band karna (ab app AI Logic nahi use karti).
11. **`android/local.properties` safai**: purane secret URLs (`UPDATE_URL`, `WEB_EXTRACT_URL`, `GOOGLE_MCP_URL`, `AZURE_ENDPOINT`) abhi bhi padhe hain. File gitignored hai.
12. **Docs refresh**: `README.md` aur `docs/ARCHITECTURE.md` purane hain (Firebase AI Logic, required Knowledge key, prompts, old toolchain, Workspace/Razorpay plans). `docs/PC_SETUP_AUDIT_2026-11-04.md` untracked hai.

**P2**
13. `AssistantViewModel` (~2,400 lines) aur `DeviceToolRunner` (~1,460 lines) todna; Hilt aur Gradle modules.
14. Persona functions (`KnowledgeBaseProvider.buildPersonaGenerationInstruction`, `validatePersonaDraft`) ab kisi UI se nahi chalte; tests ki wajah se rakhe hain.
15. Do-do jagah bikhre AWS backups: `aws-backup` folder scratchpad (temp) me hai; safe jagah copy karna agar purane payments ka record chahiye (`ultron-subscriptions.json`, 2 items).
16. Observability: Crashlytics/Analytics dependencies hain, dashboards aur CloudWatch alarms verify nahi hue.
17. Android 13+ sideload par accessibility aur notification access "restricted settings" se block hote hain; onboarding me guide chahiye.

---

## 6. Risks

| Risk | Asar | Mitigation |
|---|---|---|
| Hands-free actions + outside text | Malicious SMS/notification/web page se SMS ya call | SMS limit, untrusted warning line, default-off connectors; customers ko batana |
| Merge to `main` = sab phones par OTA | Buggy build seedha phones par | Phone test pehle, branch protection, environment gate |
| Debug-key signing | Key leak ya GitHub account compromise = malicious update | Release key par shift |
| User ki Gemini key phone par | Phone chori ya compromise | Encrypted storage (hai); key rotate karne ki salah |
| Gemini key ke bina app kaam nahi karegi | Naye user ke liye onboarding friction | Saaf onboarding screen (abhi sirf error message) |
| Live Lambdas Python 3.12 | Runtime end-of-life ke baad dikkat | 3.14 par redeploy |

---

## 7. Roadmap (suggested)

1. **Ab:** phone test v1.1.0, `local.properties` safai, Firebase console cleanup, docs refresh.
2. **Next:** long-term memory (4 phases, plan doc dekho), store + checkout + key issuance, release signing, license expiry timer, branch protection.
3. **Baad me:** SAM migration (dev/prod), Lambdas 3.14 redeploy, observability, ViewModel/DeviceToolRunner refactor.
4. **Launch:** website (download page SHA-256, privacy, terms), onboarding screen for Gemini key.

---

## 8. Quick facts

- Repo: `devxjitin/ultron-android` (local `X:\KIRA`).
- App: package `com.assistant.app`, minSdk 26, target 35, compile 36, Kotlin 2.0.20, Compose.
- Version source: `android/app/src/main/assets/changelog.md` (top `## x.y.z` heading).
- OTA: merge to `main` touching `android/**`, `scripts/publish-update.ps1`, `.github/workflows/publish-ota.yml` triggers `Publish OTA`.
- AWS account `143920986615`, user `jitin-admin`, region `us-east-1`. `future-self-booth-api` aur `/age-prediction/hf-token` doosre projects ke hain, inhe mat chhedo.
- Counts at 1.1.0: 240 Android unit tests, 59 backend tests, lint 0 errors / 43 warnings.
