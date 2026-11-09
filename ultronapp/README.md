# Ultronapp

Admin web app for Ultron activation keys. Create a key with the buyer's name, email, phone, plan and validity (all required),
and it is emailed to the buyer at once. A dashboard shows keys by state, keys expiring soon and keys created per month; the
Keys page searches, filters, sorts and exports to CSV. Each key can be edited, revoked, replaced (a lost key is revoked and a
new one emailed) or allowed to move to a new phone. The Ultron app shows these details on its Profile page after activation.

Plans and prices live in one place, `PLANS` at the top of `app.js`: Spark (7 days, ₹199), Surge (1 month, ₹699) and Apex
(3 months, ₹1,499). Choosing a plan fills the validity and the amount; both stay editable (use 0 for a gift). The amount paid
is saved on each key, so changing a price later never rewrites past sales. The dashboard adds it up as revenue, leaving out
revoked keys.

It is plain HTML and ES modules, so there is nothing to build.

## How it works

- Sign in with Google (Firebase Auth). Only emails in the stack's `AdminEmails` parameter get through; everyone else sees
  "not allowed" even though their token is valid.
- It calls `/v1/admin/licenses` on the Ultron API (`mcp/license-admin`). The table only stores each key's SHA-256, so the
  full key is shown once, right after it is created. Copy it then.

## Setup

1. The Web app "Ultron Control" already exists; its `apiKey` is in `config.js`.
   Use that key, not the Android one, which is restricted to the phone app.
2. Deploy the API stack:
   ```bash
   aws cloudformation deploy --template-file packaged.yaml --stack-name ultron-api --capabilities CAPABILITY_IAM \
     --parameter-overrides AdminEmails=you@example.com AdminOrigins=https://ultronapp-admin.web.app \
     RequestSenderEmail=no-reply@devxjitin.dev SupportEmail=devxjitin@gmail.com DownloadUrl=https://ultron.devxjitin.dev
   ```
   (see the header of `infra/api.yaml` for the package step).
3. Set up the key emails in Resend (resend.com): verify the `devxjitin.dev` domain there (the DKIM and SPF records in DNS) so
   `no-reply@devxjitin.dev` can send. Create an API key with sending access and store it once, before step 2, as an SSM
   SecureString in the same region as the stack (the stack reads it by name and never holds the key itself):
   ```bash
   aws ssm put-parameter --name /ultron/resend-api-key --type SecureString --value <the key>
   ```
   If an email fails, the key is still shown on screen to copy and the app says the email did not go out.
4. Firebase console, Authentication, Settings, Authorized domains: add the domain Ultronapp is served from.
5. Host it:
   ```bash
   firebase deploy --only hosting
   ```

## Try it without any setup

```bash
cd ultronapp
python -m http.server 8080
```

Open `http://localhost:8080/?demo=1`. Demo mode (localhost only) uses made-up in-memory data and never calls the API.
