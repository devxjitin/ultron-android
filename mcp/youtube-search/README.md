# YouTube Search MCP (AWS)

Four read-only tools implemented with `yt-dlp==2026.10.26`, without a YouTube API key:

- `youtube_search(query, limit=10)` — up to 25 videos.
- `youtube_video_info(video)` — video metadata.
- `youtube_list_videos(source, limit=20)` — up to 50 channel/playlist entries.
- `youtube_transcript(video, language="en")` — available public captions, at most 20,000 characters.

No media files, signed stream URLs, cookies, arbitrary extractor URLs or download commands are exposed. Only normalized YouTube addresses are accepted. Search and listing use flat extraction rather than fetching every video's streams. Responses mark third-party text as untrusted. Requests have a 19-second work budget on Lambda; upstream socket timeouts and retries are bounded.

YouTube can block cloud IPs, require login or refuse captions. Such failures return MCP `isError`; there is no phone fallback and no promise that arbitrary public transcripts will work from AWS. No residential proxy or paid service is configured.

## Deployment

`infra/youtube.yaml` owns a separate stack attached to the existing Ultron API. This avoids replacing existing web/license/update functions or reapplying their configuration. The new route uses the existing Firebase JWT authorizer, and the function additionally enforces Firebase App Check. No public Lambda Function URL is created.

```powershell
python mcp/youtube-search/build.py
aws cloudformation package --template-file infra/youtube.yaml --s3-bucket <private-artifact-bucket> --s3-prefix cfn/youtube --output-template-file <packaged.yaml> --region us-east-1
aws cloudformation deploy --template-file <packaged.yaml> --stack-name ultron-youtube --capabilities CAPABILITY_IAM --parameter-overrides ApiId=<existing-http-api-id> AuthorizerId=<existing-firebase-jwt-authorizer-id> --region us-east-1
```

Endpoint: `POST https://api.devxjitin.dev/v1/youtube/mcp`.
Lambda target: `ultron-api-youtube-search`, Python 3.14, 512 MB, 25-second timeout. The existing API's default route throttles and account concurrency limits apply. Reserved concurrency is omitted because this account must keep its minimum unreserved capacity.

Run offline tests with `python -B -m unittest discover -s mcp/tests -q`. Building on Windows downloads Linux Python 3.14 wheels. `appcheck.py` is bundled from the existing web-extract service, so token verification has one source.

## Android split

The existing `youtube` connector ID now owns only the four AWS lookup tools. `youtube_downloads` owns `youtube_download_options` and `download_youtube_video`. Individual tool preference names are preserved, so existing selections survive the split.

`YouTubeDownloadsMcp` is an in-process JSON-RPC MCP server inside the Android app. It supports initialization, tool discovery and tool calls, invokes the existing NewPipe downloader, and opens no network listener. It is private to Ultron, not an MCP URL for other apps. Format choice, downloads, muxing, notifications and stored media stay on the phone. It cannot execute search tools, and the AWS service cannot execute download tools.

The local server returns available formats when video quality or kind has not been chosen. Downloads continue through the existing foreground download service and save into Downloads/Ultron.

## Verification — 4 November 2026

- Stack `ultron-youtube` reached `CREATE_COMPLETE` in `us-east-1`.
- Exercised the packaged code using a temporary IAM-only Lambda on the AWS network: search returned two Python videos; metadata, channel listing and English manual captions also succeeded. The temporary function and its log group were removed afterwards. This verifies upstream extraction from AWS, not a logged-in Android walkthrough.
- Public endpoint without Firebase credentials returned HTTP 401. Direct function invocation with synthetic JWT claims but no App Check token also returned 401 `app check failed`.
- All 76 backend tests passed. Android: 236 tests in 42 suites, no failures/errors; lint: zero errors and 43 existing warnings; debug APK assembled successfully.
- CloudFormation `validate-template` and change-set creation passed; the reviewed change set only added new resources. Local cfn-lint/cfn-guard were unavailable, so their schema/compliance checks were not run. The first stack creation rolled back because reserved concurrency would violate this account's minimum unreserved capacity; the corrected template omits that setting and deployed successfully.
- No Android device was connected. Installation, authenticated app-to-gateway calls and actual media download on a phone remain unverified for this build.
