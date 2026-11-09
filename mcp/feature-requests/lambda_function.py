"""Skill and connector requests and bug reports sent from inside the Ultron app, emailed to the developer.

Route (behind the Ultron HTTP API; the Firebase ID token was already checked by API Gateway):
  POST /v1/requests  {"type": "skill"|"connector"|"bug", "name", "details", "examples"?, "email"?, "appVersion"?, "device"?}
  -> 200 {"id", "emailed"}; 400 {"error"} for a bad form; 429 {"error": "too_many"} after DAILY_LIMIT requests a day.

Every request is stored first (DynamoDB), then emailed with Resend. If the email cannot be sent the request is still
kept and the answer says "emailed": false, so nothing the user wrote is lost.

Items in the table (key "id" S):
  a request:   id (uuid), type, name, details, examples, email, appVersion, device, uid, createdAt (epoch seconds)
  a counter:   id "limit#<uid>#<yyyymmdd>", count N, expiresAt N  (how many requests that user sent that day; DynamoDB's
               time-to-live setting on expiresAt deletes it a couple of days later, so counters do not pile up)

Env vars: TABLE, DEVELOPER_EMAIL (who gets the mail), SENDER_EMAIL (an address on a domain verified in Resend), RESEND_API_KEY_PARAM (the SSM parameter that holds the Resend API key), FIREBASE_PROJECT_NUMBER and
APP_CHECK_MODE (see appcheck.py: only the genuine app may send requests, so scripts with a free anonymous account cannot).
"""
import base64
import json
import os
import re
import time
import uuid

import appcheck

DAILY_LIMIT = 5
# Counters are deleted by DynamoDB TTL once their day is long over.
COUNTER_TTL_SECONDS = 2 * 86_400
TYPES = {"skill": "Skill", "connector": "Connector", "bug": "Bug"}
# device is the phone model and Android version a bug report carries, only when the user left that option on.
LIMITS = {"name": 80, "details": 1500, "examples": 600, "email": 120, "appVersion": 20, "device": 200}
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

_table = None
_mailer = None


def _db():
    global _table
    if _table is None:
        import boto3  # Lambda runtime has it; imported here so the offline tests run without it.
        _table = boto3.resource("dynamodb").Table(os.environ["TABLE"])
    return _table


def _mail():
    global _mailer
    if _mailer is None:
        from resend_mail import ResendMail  # Imported here so the offline tests, which replace _mailer, never load it.
        _mailer = ResendMail()
    return _mailer


def _resp(status, body):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(body)}


def _json_body(event):
    body = event.get("body") or ""
    try:
        data = json.loads(base64.b64decode(body) if event.get("isBase64Encoded") else body or "{}")
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


def _uid(event):
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    return claims.get("sub") or ""


def _text(data, field):
    value = data.get(field)
    return value.strip()[:LIMITS[field]] if isinstance(value, str) else ""


def clean_form(data):
    """The validated form as a dict, or the error id (a string) when it is not acceptable."""
    kind = str(data.get("type", "")).lower()
    if kind not in TYPES:
        return "bad_type"
    form = {"type": kind, **{field: _text(data, field) for field in LIMITS}}
    if len(form["name"]) < 2:
        return "bad_name"
    if len(form["details"]) < 10:
        return "bad_details"
    if form["email"] and not EMAIL.match(form["email"]):
        return "bad_email"
    return form


def _within_daily_limit(uid, now):
    day = time.strftime("%Y%m%d", time.gmtime(now))
    count = _db().update_item(
        Key={"id": f"limit#{uid}#{day}"},
        UpdateExpression="ADD #c :one SET expiresAt = :expires",
        ExpressionAttributeNames={"#c": "count"},
        ExpressionAttributeValues={":one": 1, ":expires": now + COUNTER_TTL_SECONDS},
        ReturnValues="UPDATED_NEW",
    )["Attributes"]["count"]
    return int(count) <= DAILY_LIMIT


def _one_line(text):
    return re.sub(r"\s+", " ", text)


def _email(form, request_id):
    kind = TYPES[form["type"]]
    bug = form["type"] == "bug"
    lines = [
        f"Bug reported: {form['name']}" if bug else f"{kind} requested: {form['name']}",
        "",
        "What happened:" if bug else "What it should do:",
        form["details"],
    ]
    if form["examples"]:
        lines += ["", "What they expected:" if bug else "Example commands:", form["examples"]]
    lines += ["", f"Reply to: {form['email'] or '(not given)'}", f"App version: {form['appVersion'] or '(unknown)'}"]
    if form["device"]:
        lines.append(f"Device: {form['device']}")
    lines.append(f"Request id: {request_id}")
    subject = f"[Ultron] Bug: {form['name']}" if bug else f"[Ultron] {kind} request: {form['name']}"
    message = {
        "Subject": {"Data": _one_line(subject)[:200], "Charset": "UTF-8"},
        "Body": {"Text": {"Data": "\n".join(lines), "Charset": "UTF-8"}},
    }
    params = {
        "Source": os.environ["SENDER_EMAIL"],
        "Destination": {"ToAddresses": [os.environ["DEVELOPER_EMAIL"]]},
        "Message": message,
    }
    if form["email"]:
        params["ReplyToAddresses"] = [form["email"]]
    _mail().send_email(**params)


def submit(event):
    data = _json_body(event)
    if data is None:
        return _resp(400, {"error": "bad_request"})
    form = clean_form(data)
    if isinstance(form, str):
        return _resp(400, {"error": form})
    uid = _uid(event)
    now = int(time.time())
    if not _within_daily_limit(uid, now):
        return _resp(429, {"error": "too_many"})
    request_id = str(uuid.uuid4())
    _db().put_item(Item={"id": request_id, **{k: v for k, v in form.items() if v != ""}, "uid": uid, "createdAt": now})
    try:
        _email(form, request_id)
        emailed = True
    except Exception:  # Resend key missing, sender not verified, throttled: the request is stored, the developer can still read it.
        emailed = False
    return _resp(200, {"id": request_id, "emailed": emailed})


ROUTES = {("POST", "/v1/requests"): submit}


def lambda_handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "")
    handler = ROUTES.get((method, event.get("rawPath", "")))
    if handler is None:
        return _resp(404, {"error": "not found"})
    if not _uid(event):
        return _resp(401, {"error": "sign in required"})
    refused = appcheck.check(event)
    if refused:
        return refused
    return handler(event)
