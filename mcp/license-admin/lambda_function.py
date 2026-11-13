"""Admin API behind Ultronapp (the web app that manages Ultron activation keys). Only the people listed in ADMIN_EMAILS
can use it; the app itself never calls these routes.

Routes (behind the Ultron HTTP API; API Gateway already checked the Firebase ID token, this Lambda then checks that the
token belongs to a verified admin email):
  GET   /v1/admin/licenses                      -> {"licenses": [...]} every key, newest first
  POST  /v1/admin/licenses                      -> creates keys, answers {"keys": ["ULTR-..."], "licenses": [...],
                                                   "emailed": true|false}; name, email, phone, plan and durationDays are
                                                   required, amount (rupees paid per key, default 0) is optional. The
                                                   full key is shown only in this answer and in the email
                                                   to the buyer ("sendEmail": false skips the email); the table keeps
                                                   just its SHA-256
  PATCH /v1/admin/licenses/{id}                 -> edits name, email, phone, note, plan, status, durationDays, expiresAt, amount
  POST  /v1/admin/licenses/{id}/allow-move      -> lets the key move to another phone right now (skips the one-day wait)
  POST  /v1/admin/licenses/{id}/reissue         -> a key cannot be read back, so this revokes it, creates a new one with the
                                                   same buyer and plan, and emails that one

{id} is the key's SHA-256, the same value as "id" in the list. Items are the ones mcp/license reads and writes.

Env vars: TABLE, ADMIN_EMAILS (comma separated; empty locks everyone out). For the key email: SENDER_EMAIL (an address on a
domain verified in Resend, such as no-reply@devxjitin.dev), RESEND_API_KEY_PARAM (the SSM parameter that
holds the Resend API key), SUPPORT_EMAIL (where buyers can write) and DOWNLOAD_URL (the app download page).
"""
import base64
import hashlib
import html
import json
import os
import re
import secrets
import time

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
MAX_CREATE = 50
MAX_DAYS = 3650
MAX_AMOUNT = 1_000_000  # rupees per key
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PHONE_RE = re.compile(r"^[0-9+()\-\s.]{3,30}$")


# Text fields an admin can set, with their maximum length.
TEXT_FIELDS = {"name": 80, "email": 120, "phone": 30, "note": 300, "plan": 40}

# Buyer details every key must carry, so the owner of a key can always be found and contacted.
REQUIRED_DETAILS = ("name", "email", "phone")

_table = None
_mailer = None


def _mail():
    global _mailer
    if _mailer is None:
        from resend_mail import ResendMail  # Imported here so the offline tests, which replace _mailer, never load it.
        _mailer = ResendMail()
    return _mailer


def _db():
    global _table
    if _table is None:
        import boto3  # Lambda runtime has it; imported here so the offline tests run without it.
        _table = boto3.resource("dynamodb").Table(os.environ["TABLE"])
    return _table


def new_key():
    body = "".join(secrets.choice(ALPHABET) for _ in range(16))
    return "-".join(["ULTR"] + [body[i:i + 4] for i in range(0, 16, 4)])


def key_hash(key):
    return hashlib.sha256(key.encode()).hexdigest()


def _resp(status, body):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(body)}


def _json_body(event):
    body = event.get("body") or ""
    try:
        data = json.loads(base64.b64decode(body) if event.get("isBase64Encoded") else body or "{}")
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


def _is_admin(event):
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    email = str(claims.get("email", "")).strip().lower()
    verified = str(claims.get("email_verified", "")).lower() == "true"
    allowed = {e.strip().lower() for e in os.environ.get("ADMIN_EMAILS", "").split(",") if e.strip()}
    return bool(email) and verified and email in allowed


def _state(item, now):
    """One word the web app can show: revoked, expired, unused (never activated), active."""
    if item.get("status") != "active":
        return "revoked"
    expires = int(item.get("expiresAt", 0))
    if expires and expires <= now:
        return "expired"
    return "active" if item.get("uid") else "unused"


def _public(item, now):
    return {
        "id": item["keyHash"],
        "hint": str(item.get("hint", "")),
        "state": _state(item, now),
        "status": str(item.get("status", "")),
        "plan": str(item.get("plan", "")),
        "durationDays": int(item.get("durationDays", 0)),
        "expiresAt": int(item.get("expiresAt", 0)),
        "createdAt": int(item.get("createdAt", 0)),
        "activatedAt": int(item.get("activatedAt", 0)),
        "movedAt": int(item.get("movedAt", 0)),
        "onDevice": bool(item.get("uid")),
        "name": str(item.get("name", "")),
        "email": str(item.get("email", "")),
        "phone": str(item.get("phone", "")),
        "note": str(item.get("note", "")),
        "emailedAt": int(item.get("emailedAt", 0)),
        "amount": int(item.get("amount", 0)),
        "reissuedFrom": str(item.get("reissuedFrom", "")),
    }


def _clean_text(data, field, required=False):
    """Returns (value, error); value is None when the field was not sent."""
    if field not in data:
        return (None, f"{field}_required") if required else (None, None)
    value = str(data[field] if data[field] is not None else "").strip()
    if len(value) > TEXT_FIELDS[field]:
        return None, f"{field}_too_long"
    if field == "email" and value and not EMAIL_RE.match(value):
        return None, "bad_email"
    if field == "phone" and value and not PHONE_RE.match(value):
        return None, "bad_phone"
    if field == "plan" and not value:
        return None, "plan_required"
    return value, None


def _clean_int(data, field, high):
    if field not in data:
        return None, None
    value = data[field]
    if isinstance(value, bool) or not isinstance(value, int) or value < 0 or value > high:
        return None, f"bad_{field}"
    return value, None


def list_licenses(event, now):
    items = []
    kwargs = {}
    while True:
        page = _db().scan(**kwargs)
        items.extend(page.get("Items", []))
        if not page.get("LastEvaluatedKey"):
            break
        kwargs["ExclusiveStartKey"] = page["LastEvaluatedKey"]
    licenses = [_public(i, now) for i in items]
    licenses.sort(key=lambda l: l["createdAt"], reverse=True)
    return _resp(200, {"licenses": licenses})


def _make_keys(count, fields, plan, days, now, amount=0):
    """Stores [count] new keys and returns (keys, public items). The keys are never stored, only their hashes."""
    keys, created = [], []
    for _ in range(count):
        key = new_key()
        item = {"keyHash": key_hash(key), "status": "active", "plan": plan, "durationDays": days, "expiresAt": 0,
                "createdAt": now, "hint": key[-4:], "amount": amount, **fields}
        _db().put_item(Item=item, ConditionExpression="attribute_not_exists(keyHash)")
        keys.append(key)
        created.append(item)
    return keys, created


def _send_keys(keys, items):
    """Emails [keys] to the buyer on [items][0]. Returns True when Resend accepted it and records emailedAt."""
    first = items[0]
    try:
        paid = sum(int(i.get("amount", 0)) for i in items)
        _mail().send_email(**_key_email(first.get("name", ""), first["email"], keys, first["plan"], first["durationDays"], paid))
    except Exception:  # Resend key missing, sender not verified, throttled: the admin still has the keys on screen.
        return False
    sent_at = int(time.time())
    for item in items:
        try:
            _db().update_item(
                Key={"keyHash": item["keyHash"]}, UpdateExpression="SET #e = :t",
                ExpressionAttributeNames={"#e": "emailedAt"}, ExpressionAttributeValues={":t": sent_at},
            )
            item["emailedAt"] = sent_at
        except Exception:  # The mail is out; a missing "sent" mark must not turn that into an error.
            pass
    return True


def create(event, now):
    data = _json_body(event)
    if data is None:
        return _resp(400, {"error": "bad_json"})
    fields = {}
    for field in REQUIRED_DETAILS:
        value, error = _clean_text(data, field)
        if error:
            return _resp(400, {"error": error})
        if not value:
            return _resp(400, {"error": f"{field}_required"})
        fields[field] = value
    note, error = _clean_text(data, "note")
    if error:
        return _resp(400, {"error": error})
    if note:
        fields["note"] = note
    plan, error = _clean_text({"plan": data.get("plan", "")}, "plan")
    if error:
        return _resp(400, {"error": error})
    days, error = _clean_int(data, "durationDays", MAX_DAYS)
    if error:
        return _resp(400, {"error": error})
    if days is None:
        return _resp(400, {"error": "durationDays_required"})
    count, error = _clean_int(data, "count", MAX_CREATE)
    if error or count == 0:
        return _resp(400, {"error": "bad_count"})
    count = count or 1
    amount, error = _clean_int(data, "amount", MAX_AMOUNT)
    if error:
        return _resp(400, {"error": error})
    send = data.get("sendEmail", True) is not False

    keys, items = _make_keys(count, fields, plan, days, now, amount or 0)
    emailed = _send_keys(keys, items) if send else False
    return _resp(200, {"keys": keys, "licenses": [_public(i, now) for i in items], "emailed": emailed})


def reissue(event, now, license_id):
    """Replaces a key the buyer lost: the old one is revoked, a new one with the same details is created and emailed."""
    old = _db().get_item(Key={"keyHash": license_id}).get("Item")
    if old is None:
        return _resp(404, {"error": "not_found"})
    fields = {f: old[f] for f in (*REQUIRED_DETAILS, "note") if old.get(f)}
    if not all(fields.get(f) for f in REQUIRED_DETAILS):
        return _resp(400, {"error": "details_missing"})
    # A replacement carries no amount: the buyer already paid for the key it replaces.
    # A key already running keeps its end date, so a lost-key replacement never restarts the purchased term.
    # An unused key (expiresAt 0) still starts its term at first activation; so does a lifetime key.
    carried = {"expiresAt": int(old["expiresAt"])} if int(old.get("expiresAt", 0)) else {}
    keys, items = _make_keys(1, {**fields, **carried, "reissuedFrom": license_id}, str(old.get("plan", "")), int(old.get("durationDays", 0)), now, 0)
    # Only once the new key exists is the old one switched off, so a failure never leaves the buyer with nothing.
    _apply(license_id, {"status": "revoked"}, now)
    emailed = _send_keys(keys, items)
    return _resp(200, {"keys": keys, "licenses": [_public(i, now) for i in items], "emailed": emailed})


def _key_email(name, to, keys, plan, days, paid=0):
    """The SES-shaped arguments (see resend_mail.py) for the mail that carries the activation key(s) to the buyer."""
    sender = os.environ["SENDER_EMAIL"]
    support = os.environ.get("SUPPORT_EMAIL", "")
    download = os.environ.get("DOWNLOAD_URL", "")
    greeting = f"Hi {name}," if name else "Hi,"
    plural = len(keys) > 1
    validity = ("never expires" if days == 0 else f"is valid for {days} days from the day you first activate it")
    steps = [
        "Download and install the Ultron app" + (f" from {download}" if download else "") + ".",
        "If your phone shows a Play Protect warning, choose More details, then Install anyway. Ultron asks for "
        "accessibility and notification access so it can work your phone by voice, and Play Protect warns about that "
        "for apps installed outside the Play Store.",
        "Open Ultron and paste your activation key when asked.",
        "Say hello.",
    ]
    paid_text = f" Amount paid: Rs. {paid:,}." if paid else ""
    text = "\n".join([
        greeting, "",
        f"Thank you for choosing Ultron. Here {'are your activation keys' if plural else 'is your activation key'}:", "",
        *keys, "",
        f"Plan: {plan.title()}.{paid_text} Each key works on one phone at a time and {validity}.", "",
        "Getting started", *[f"{i}. {step}" for i, step in enumerate(steps, 1)], "",
        "Keep this email safe: anyone who has the key can use it." + (f" Need help? Write to {support}." if support else ""),
        "", "Ultron by Nandivex Technologies",
    ])
    esc = html.escape
    key_rows = "".join(
        f'<div style="font:600 20px/1.4 Consolas,Menlo,monospace;letter-spacing:1px;background:#f3f4f6;border-radius:10px;'
        f'padding:14px 16px;margin:8px 0;text-align:center;color:#111827">{esc(k)}</div>' for k in keys
    )
    step_rows = "".join(f'<li style="margin:6px 0">{esc(step)}</li>' for step in steps)
    button = (f'<p style="margin:20px 0"><a href="{esc(download)}" style="background:#c8102e;color:#ffffff;text-decoration:none;'
              f'padding:12px 22px;border-radius:999px;font-weight:600;display:inline-block">Download Ultron</a></p>') if download else ""
    help_line = f' Need help? Write to <a href="mailto:{esc(support)}" style="color:#c8102e">{esc(support)}</a>.' if support else ""
    body_html = (
        '<div style="background:#f6f6f7;padding:24px 12px;font:15px/1.6 -apple-system,Segoe UI,Roboto,Arial,sans-serif;color:#1f2937">'
        '<div style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:16px;padding:28px 26px">'
        '<div style="font:700 22px/1 Georgia,serif;color:#111827;margin-bottom:18px">Ultron</div>'
        f'<p style="margin:0 0 12px">{esc(greeting)}</p>'
        f'<p style="margin:0 0 6px">Thank you for choosing Ultron. Here {"are your activation keys" if plural else "is your activation key"}:</p>'
        f'{key_rows}'
        f'<p style="margin:12px 0;color:#4b5563">Plan: <b>{esc(plan.title())}</b>.{esc(paid_text)} Each key works on one phone at a time and {esc(validity)}.</p>'
        f'{button}'
        '<h3 style="margin:22px 0 6px;font-size:16px">Getting started</h3>'
        f'<ol style="margin:0;padding-left:20px">{step_rows}</ol>'
        f'<p style="margin:22px 0 0;color:#6b7280;font-size:13px">Keep this email safe: anyone who has the key can use it.{help_line}</p>'
        '</div></div>'
    )
    message = {
        "Subject": {"Data": "Your Ultron activation key" if not plural else "Your Ultron activation keys", "Charset": "UTF-8"},
        "Body": {"Text": {"Data": text, "Charset": "UTF-8"}, "Html": {"Data": body_html, "Charset": "UTF-8"}},
    }
    params = {"Source": f"Ultron <{sender}>", "Destination": {"ToAddresses": [to]}, "Message": message}
    if support:
        params["ReplyToAddresses"] = [support]
    return params


def update(event, now, license_id):
    data = _json_body(event)
    if data is None:
        return _resp(400, {"error": "bad_json"})
    changes = {}
    for field in TEXT_FIELDS:
        value, error = _clean_text(data, field)
        if error:
            return _resp(400, {"error": error})
        if value is not None:
            if field in REQUIRED_DETAILS and not value:
                return _resp(400, {"error": f"{field}_required"})
            changes[field] = value
    if "status" in data:
        if data["status"] not in ("active", "revoked"):
            return _resp(400, {"error": "bad_status"})
        changes["status"] = data["status"]
    for field, high in (("durationDays", MAX_DAYS), ("expiresAt", 4_102_444_800), ("amount", MAX_AMOUNT)):
        value, error = _clean_int(data, field, high)
        if error:
            return _resp(400, {"error": error})
        if value is not None:
            changes[field] = value
    if not changes:
        return _resp(400, {"error": "nothing_to_change"})
    return _apply(license_id, changes, now)


def allow_move(event, now, license_id):
    return _apply(license_id, {"movedAt": 0}, now)


def _apply(license_id, changes, now):
    names = {f"#{f}": f for f in changes}
    values = {f":{f}": v for f, v in changes.items()}
    expression = "SET " + ", ".join(f"#{f} = :{f}" for f in changes)
    try:
        updated = _db().update_item(
            Key={"keyHash": license_id},
            UpdateExpression=expression,
            ConditionExpression="attribute_exists(keyHash)",
            ExpressionAttributeNames=names,
            ExpressionAttributeValues=values,
            ReturnValues="ALL_NEW",
        )["Attributes"]
    except Exception as e:  # ConditionalCheckFailedException: no such key.
        if "ConditionalCheckFailed" in type(e).__name__ or "ConditionalCheckFailed" in str(e):
            return _resp(404, {"error": "not_found"})
        raise
    return _resp(200, {"license": _public(updated, now)})


def lambda_handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "")
    path = event.get("rawPath", "").rstrip("/")
    if not _is_admin(event):
        return _resp(403, {"error": "forbidden"})
    now = int(time.time())
    if path == "/v1/admin/licenses":
        if method == "GET":
            return list_licenses(event, now)
        if method == "POST":
            return create(event, now)
    match = re.fullmatch(r"/v1/admin/licenses/([0-9a-f]{64})(/allow-move|/reissue)?", path)
    if match:
        license_id, suffix = match.groups()
        if method == "PATCH" and not suffix:
            return update(event, now, license_id)
        if method == "POST" and suffix == "/allow-move":
            return allow_move(event, now, license_id)
        if method == "POST" and suffix == "/reissue":
            return reissue(event, now, license_id)
    return _resp(404, {"error": "not found"})
