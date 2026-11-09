# Bacha hua kaam (2026-11-09)

Done = merge ho chuka. Sab kuch sirf build aur unit tests se verify hua hai, phone par test aap karoge.

## Ho chuka (main mein)

| Version | PR | Kya |
|---|---|---|
| 1.18.3 | #79 | UI fixes, dead code hataya, repo tidy |
| 1.18.4 | #80 | Parallel knowledge search, gentle go-away, delayed warm-up |
| 1.18.5 | #81 | Knowledge import mein batch embeddings (50 per call) |
| 1.18.6 | #82 | `AssistantScreen` ke callbacks `AssistantActions` mein |
| 1.19.0 | #83 | Knowledge sirf ObjectBox mein (DataStore se verified import) |
| 1.19.1 | #84 | CI: APK checks ke saath parallel banta hai, Gradle ko zyada memory, duplicate lint hata. Publish 16 min se 6.5-9 min |
| 1.19.2 | #85 | Purani DataStore copy aur import code hataya |
| 1.19.3 | #86 | `AssistantViewModel` ke do `init` blocks ek |
| 1.19.4 | #87 | Baseline profile + `profileinstaller` |
| 1.19.5 | #88 | `UpdateManager`: update check aur install `AssistantViewModel` se alag |
| 1.19.6 | #89 | `AudioRouting`: audio focus aur speaker routing |
| 1.19.7 | #90 | `KnowledgeManager`: documents, embedding migration, knowledge search |
| 1.19.8 | #91 | `ToolDispatcher` aur `ToolRunner`: tool calls |
| 1.19.9 | #92 | `SessionTimers` aur `SpeechDetector`: session ke timers aur bolna pehchanna |
| 1.19.10 | #93 | `VoiceSessionController`: poora voice session. `AssistantViewModel` 2,626 se ~930 lines |

## Phone par test karna hai (aapka kaam)

0. **1.19.5 se 1.19.10 (ViewModel split):** sirf jagah badli hai, behaviour wahi hona chahiye. Sabse zyada dhyan `VoiceSessionController` (1.19.10) par: cold start mic tap, jaldi tap, beech mein interrupt, mute, tool call, session ke dauran phone call, background aur notification Stop, Wi-Fi band/chalu, 2 minute idle aur 15 minute limit, Settings mein voice/language badalna. `AudioRouting` ke liye Bluetooth/speaker aur call ke baad focus.
1. **1.19.2:** Knowledge ke documents dikhte hain, search unhe dhundhta hai, add/edit/delete/reset chalte hain, restart ke baad bhi data wahi hai. Dhyan: jis phone ne 1.19.0 ke baad Knowledge kabhi nahi kholi, uske purane documents ab import nahi honge (dobara add karne padenge).
2. **1.19.4:** cold start ka farak dekho. Update ke baad pehli start jaisi hi ho sakti hai (profile background mein compile hoti hai); fayda agli starts se dikhega.
3. **1.18.5:** bada document add karo, import tez ho aur progress bar sahi chale.
4. **1.18.6:** Home, Settings, Memory, Connectors, Knowledge sab pehle jaisa chale.
5. **1.18.4:** knowledge sawaal tez aaye; app kholte hi 2 second ke andar mic tap karo.

## Aage ka kaam

1. **Debug logs:** `Log.i/d` calls kam karna (chhota, fayda kam).
2. ~~`AssistantViewModel` todna~~: ho gaya (1.19.5 se 1.19.10), plan `docs/VIEWMODEL_SPLIT_PLAN.md` mein. `VoiceSessionController` aur `AudioRouting` ke unit tests nahi hain (asli mic, speaker aur Gemini connection); phone test hi unki jaanch hai.
3. **Naapi hui baseline profile:** phone par Macrobenchmark `BaselineProfileRule` chalake `baseline-prof.txt` badalna. Abhi wildcard wali andaze ki profile hai.
4. **Chhoti safai (kam fayda):** `LegacyScheduleCleanup` kuch versions baad hatana; `PhoneConnectors` / `VisitorStrings` wrappers.
5. **`sites/ultron-showcase/`:** alag repo (`devxjitin/ultron-website`), isliye yahan ignore hai. Uski branch `feat/pricing-install-legal` mein ek commit ("Send orders to WhatsApp") abhi push nahi hua.

## Jaanch ke baad non-issue nikle (kuch nahi badla)
- `savingKnowledgeDocumentTarget` failure par clear nahi hota: jaan-boojh kar, error sahi card ke neeche dikhane ke liye.
- `REPLY_NOTIFICATION` turant bhejta hai: tool description "sirf jab user ne clearly kaha" kehta hai, aur extra confirm nahi chahiye.
- Plan prices: admin app mein `PLANS` ek hi jagah hai (`ultronapp/app.js`); `demo.js` nakli data hai.
- ConnectorsPage ke system checks pehle se memoized hain.

## Merge ka niyam
`main` par ek ke baad ek jaldi merge mat karo. Pehle merge ka Publish OTA khatam hone do, warna pichhla publish "checkout is behind origin/main" se fail dikhta hai. Do PR ek hi jagah changelog badlein to doosre ko naye main par rebase karo.
