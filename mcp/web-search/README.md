# Web Search MCP (AWS)

One read-only tool, `web_search(query, time?, limit=6)`, built on the [DDGS](https://pypi.org/project/ddgs/) metasearch library (`ddgs==9.16.1`). No API key.

- `query`: up to 500 characters.
- `time`: `day`, `week`, `month` or `year` (optional).
- `limit`: 1 to 10 results.

Results are trimmed to title, link and a 500-character excerpt, only `http(s)` links are returned, and the response is marked as untrusted text. Search engines can rate-limit cloud IPs; that is returned as an MCP tool error and the Android app then falls back to searching from the phone.

## Deployment

`infra/search.yaml` owns a separate stack attached to the existing Ultron API, with the existing Firebase JWT authorizer and App Check, like the YouTube stack. No public Function URL is created.

```powershell
python mcp/web-search/build.py
aws cloudformation package --template-file infra/search.yaml --s3-bucket <private-artifact-bucket> --s3-prefix cfn/search --output-template-file <packaged.yaml> --region us-east-1
aws cloudformation deploy --template-file <packaged.yaml> --stack-name ultron-search --capabilities CAPABILITY_IAM --parameter-overrides ApiId=<existing-http-api-id> AuthorizerId=<existing-firebase-jwt-authorizer-id> --region us-east-1
```

Endpoint: `POST https://api.devxjitin.dev/v1/search/mcp`. Lambda: `ultron-api-web-search`, Python 3.14, 256 MB, 25-second timeout.

Run offline tests with `python -B -m unittest discover -s mcp/tests -q`. `appcheck.py` is bundled from `web-extract`.
