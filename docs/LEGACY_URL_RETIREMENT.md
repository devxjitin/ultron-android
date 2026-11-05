# Purani Function URLs hatane ka plan

Ye plan 2026-11-03 ko AWS ki read-only jaanch (us-east-1, account 143920986615) ke baad likha gaya hai. Is jaanch me kuch badla nahi gaya.

## Abhi kya live hai

| Function | Function URL auth | Pichle 14 din me chali | Aakhri baar | APK me URL? |
|---|---|---|---|---|
| `web-extract-mcp` | NONE (path token) | 13 baar | 2026-10-26 | Nahi, `b6d07f4` me hata diya |
| `google-workspace-mcp` | NONE (path token) | 519 baar | 2026-10-28 | Nahi, app me kahin wire nahi hai |
| `ultron-updates` | NONE (path token) | 623 baar | 2026-11-02 | Haan, `UPDATE_URL` (`AppUpdater.kt:83`) |
| `ultron-api-web-extract` (naya) | Nahi hai, API Gateway ke peeche | 0 baar | - | `/v1/web/mcp` |

Do baatein jo plan ko badalti hain:

1. `google-workspace-mcp` bhi open Function URL ke saath live hai. Pehle review me ye chhoot gaya tha. Ye Google OAuth token forward karta hai, to iske path token ka leak hona sabse bura hai.
2. Naya `ultron-api-web-extract` 14 din me ek baar bhi nahi chala. Ya to phones abhi purane build pe hain, ya `/v1/web/mcp` ka route sach me kaam nahi karta. Purana URL hatane se pehle ye pakka karna zaroori hai, nahi to web page reader band ho sakta hai.

## Order

**Step 0, naye path ko jaancho (pehle, kuch delete nahi)**

- Naye build ko phone pe install karo aur "is page ka summary do" jaisa kuch bolo.
- CloudWatch me `ultron-api-web-extract` ke invocations dekho. Ek invocation dikhni chahiye, aur reply me page ka text aana chahiye.
- Agar nahi chala, to pehle route ya authorizer theek karo. Aage mat badho.

**Step 1, `google-workspace-mcp` band karo (sabse jaldi)**

- App me ye connector hai hi nahi, aur 6 din se koi call nahi aayi.
- Function URL config delete karo, Lambda abhi rehne do. Isse URL turant dead ho jata hai aur wapas banana ek command hai.
- 14 din error alarm ya CloudWatch me koi complaint na aaye to Lambda, role `google-workspace-mcp-role` aur log group delete karo.
- Repo me: `mcp/google-workspace/` aur `infra/ultron.yaml` ke Google wale resources hatao (ye alag commit me, jab Step 1 confirm ho jaye).

**Step 2, `web-extract-mcp` band karo**

- Shart: Step 0 pass ho chuka ho, aur naya APK kam se kam 7 din ek jaise users pe chala ho.
- Function URL config delete karo. 14 din baad Lambda aur uska role/log group.
- `mcp/web-extract/` ka code `ultron-api-web-extract` ke liye chahiye, use mat hatao. Sirf purani hand-made function hatani hai.

**Step 3, `ultron-updates` aur `UPDATE_URL` (sabse last)**

- Ye tab tak nahi hata sakte jab tak saare phones naye APK pe na aa jayein, kyunki purane APK isi URL se update dhundhte hain. Abhi bhi roz 3 se 120 calls aa rahi hain.
- Pehle ek release banao jisme `UPDATE_URL` build se hata ho (`AppUpdater.kt` ab sirf `ApiClient` se chale), aur use OTA se phones tak pahunchao.
- `ultron-updates` ke invocations 14 din tak zero hone ka intezaar karo. Tab Function URL, phir Lambda delete karo.
- Releases ka S3 bucket mat chhedo, naya API wahi use karta hai.

## Rollback

- Function URL delete karna ulta ho sakta hai, magar URL badal jata hai (naya random id). Isliye Step 1 aur 2 me pehle sirf URL hata rahe hain, Lambda nahi.
- Delete se pehle Lambda ka zip ya code S3 pe save rakho.

## Jo tumhe karna hai, mujhe AWS pe kuch delete karne ki permission chahiye

Maine AWS pe sirf padha hai. Delete ya URL hatana tumhari "haan" ke baad hi karunga, ek ek step karke.

## Related cheezein

- CI ka `LOCAL_PROPERTIES` secret check karo ki usme `WEB_EXTRACT_URL`, `UPDATE_URL` ya Gemini keys to nahi hain. Hain to hata do, aur path tokens rotate karo.
- `docs/ARCHITECTURE.md` ke T3 aur S3 rows is plan ke baad update honge.

## Step 0 ka nateeja (2026-11-03, read-only jaanch)

Server side pura theek hai:

- `POST /v1/web/mcp` route Firebase JWT authorizer ke peeche hai (issuer `securetoken.google.com/ultron-5350c`), integration `ultron-api-web-extract` pe jata hai, aur Lambda ki permission sirf is route ke liye hai.
- Bina token ke API 401 deta hai (`/v1/web/mcp` aur `/v1/releases/latest` dono).
- Lambda me `JWT_AUTH=1` hai, to wo path `/v1/web/mcp` maanta hai. Maine ek synthetic event se seedha invoke kiya (`https://example.com` padhne ko), aur 200 ke saath page ka text wapas aaya. Matlab code, dependencies aur network sab chal rahe hain.
- "0 invocations" ka kaaran: naya API sach me nayi cheez hai. Uski pehli traffic 1 Nov ko aayi (`ultron-api-releases` 21 calls), 2 Nov ko license aur releases. Is dauran kisi ne web page reader use nahi kiya. Ye koi kharabi nahi hai.

Abhi bhi baaki hai:

- Phone se asli test (Firebase token ke saath). Ye main nahi kar sakta. Naya build install karke "is page ka summary do" bolo, phir CloudWatch me `ultron-api-web-extract` ki ek invocation dekho.
- `APP_CHECK_MODE` abhi `log` hai, yaani bina App Check token ke bhi request chal jati hai. Jab saare phones naye build pe aa jayein, ise `enforce` karo.
- Naya `ultron-api-web-extract` Function URL se bhi bina auth ke band hai (uska koi Function URL nahi hai), jo sahi hai.

### Phone test (2026-11-03 20:58 IST, debug build `adb install -r` ke baad)

Pass. Phone ne Firebase token ke saath `/v1/web/mcp` pe teen calls ki (15:28 UTC): `initialize` (99 ms), `notifications/initialized` (2 ms) aur `tools/call` (1.9 s, page padha). `ultron-api-web-extract` pe 0 errors, aur purana `web-extract-mcp` pe 0 calls, yaani naya build sirf naye raaste se gaya. Step 0 ho gaya, Step 1 ab ho sakta hai. Step 2 ke liye ab bhi chahiye ki naya APK baaki phones pe kuch din chale.

## Step 1 ho gaya (2026-11-03)

`google-workspace-mcp` ka Function URL delete kar diya (us-east-1). Lambda, uska role aur log group abhi hain. Purani URL ab HTTP 403 deti hai.

Rollback agar zaroorat pade (URL ka host badal jayega, kyunki naya id banta hai):

```
aws lambda create-function-url-config --function-name google-workspace-mcp --auth-type NONE --invoke-mode BUFFERED --region us-east-1
```

Isme resource policy pehle se hai, to sirf URL config dobara banana kaafi hai.

Baaki:

- 14 din (2026-11-17) tak koi complaint ya error na aaye to Lambda `google-workspace-mcp`, role `google-workspace-mcp-role` aur uska log group delete karo.
- Uske baad repo se `mcp/google-workspace/` aur `infra/ultron.yaml` ke Google resources hatao. Template abhi bhi is URL ko declare karta hai, aur use us stack pe deploy mat karo, warna URL wapas aa jayega.

## Step 2 aur 3 ho gaye (2026-11-03)

App sirf ek phone pe hai, aur uska build (`25603a8`) na `WEB_EXTRACT_URL` use karta hai na `UPDATE_URL`. Install ke baad CloudWatch me purani dono Lambdas pe 0 calls thi, aur update check sirf `ultron-api-releases` se hua. Isliye `web-extract-mcp` aur `ultron-updates` ke Function URL bhi delete kar diye. Dono ab HTTP 403 dete hain. Lambdas, roles aur log groups abhi hain.

Rollback (URL ka host badal jayega, kyunki naya id banta hai):

```
aws lambda create-function-url-config --function-name web-extract-mcp --auth-type NONE --invoke-mode BUFFERED --region us-east-1
aws lambda create-function-url-config --function-name ultron-updates --auth-type NONE --invoke-mode BUFFERED --region us-east-1
```

Baaki:

- 2026-11-17 tak koi error na aaye to `web-extract-mcp`, `ultron-updates`, `google-workspace-mcp` Lambdas, unke roles aur log groups delete karo.
- `infra/ultron.yaml` ab bhi in URLs ko declare karta hai. Use us stack pe deploy mat karo. Lambdas hatane ke baad template bhi hata do.
- `ultron-api-web-extract` aur `mcp/web-extract/` rehne do, naya API inhi pe chalta hai.
- `docs/ARCHITECTURE.md` ke T3 aur S3 rows update karne hain.

## App Check enforce aur infra cleanup (2026-11-03)

- `APP_CHECK_MODE=enforce` set kiya `ultron-api-releases` aur `ultron-api-web-extract` pe (baaki env vars waise hi rakhe). Pehle ke logs me 64 releases requests me sirf 5 pe token missing tha (purane test calls), to asli phone tokens valid the. Bina token ke synthetic request ab 401 `app check failed` deta hai. Phone pe About me "Check again" 2 baar chalaya (15:36 aur 15:37 UTC), update check enforce ke saath chala, aur logs me koi App Check problem nahi aaya.
- Rollback: `aws lambda update-function-configuration` se `APP_CHECK_MODE` wapas `log` karo (baaki env vars bhi saath bhejna, warna wo hat jate hain).
- `infra/api.yaml` ka `AppCheckMode` default ab `enforce` hai.
- `infra/ultron.yaml` se dono Function URL, unki permissions aur outputs hata diye. Ab ye template deploy karne se URL wapas nahi banenge.
- GitHub secret `LOCAL_PROPERTIES` me ab sirf `GITHUB_CLIENT_ID` hai (build me sirf wahi padha jata hai). `UPDATE_URL`, `WEB_EXTRACT_URL`, `GOOGLE_MCP_URL`, `AZURE_ENDPOINT` aur `GOOGLE_WEB_CLIENT_ID` hat gaye. Purani value padh nahi sakte the, isliye secret ko local.properties ki sahi line se naya bana diya.

## Legacy resources deleted (2026-11-05)

At the owner's request the three legacy Lambdas (`google-workspace-mcp`, `ultron-updates`, `web-extract-mcp`), their roles and log groups, the `ultron-subscriptions` table and the `/ultron/razorpay/*` SSM parameters were deleted from us-east-1. This was done before the 17 November observation date. The `ultron-api` and `ultron-youtube` stacks and the `ultron-licenses` table remain, and `POST /v1/license/status` still answers 401 without a token.
