# Web Search MCP (AWS)

One read-only tool, `web_search(query, time?, limit=6)`, built on the [DDGS](https://pypi.org/project/ddgs/) metasearch library (`ddgs==9.16.1`). No API key.

- `query`: up to 500 characters.
- `time`: `day`, `week`, `month` or `year` (optional).
- `limit`: 1 to 10 results.

Results are trimmed to title, link and a 500-character excerpt, only `http(s)` links are returned, and the response is marked as untrusted text. Search engines can rate-limit cloud IPs; that is returned as an MCP tool error and the Android app then falls back to searching from the phone.

## Deployment

`infra/api.yaml` (the `ultron-api` stack) owns this function and its route. Keep every route in that template: each deploy re-imports the API from the routes found there and deletes any route created from another stack. That is how the search and YouTube routes went missing on 7 November 2026, and why the two separate stacks were removed. The route uses the Firebase JWT authorizer, and the function also enforces Firebase App Check. No public Lambda Function URL is created.

```powershell
python mcp/web-extract/build.py
python mcp/web-search/build.py
aws cloudformation package --template-file infra/api.yaml --s3-bucket <private-artifact-bucket> --s3-prefix cfn --output-template-file <packaged.yaml> --region us-east-1
aws cloudformation deploy --template-file <packaged.yaml> --stack-name ultron-api --capabilities CAPABILITY_IAM --region us-east-1
```

Run the build of every function whose zip is named in `infra/api.yaml` first. Parameters you do not override keep their live values (domain, admin emails and so on).

Endpoint: `POST https://api.devxjitin.dev/v1/search/mcp`. Lambda: `ultron-api-web-search`, Python 3.14, 256 MB, 25-second timeout.

Run offline tests with `python -B -m unittest discover -s mcp/tests -q`. `appcheck.py` is bundled from `web-extract`.
