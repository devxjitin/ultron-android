"""Admin API behind Ultronapp (the web app that manages Ultron activation keys). Only the people listed in ADMIN_EMAILS
can use it; the app itself never calls these routes.

Routes (behind the Ultron HTTP API; API Gateway already checked the Firebase ID token, this Lambda then checks that the
token belongs to a verified admin email):
  GET   /v1/admin/licenses                      -> {"licenses": [...]} every key, newest first
  POST  /v1/admin/licenses                      -> creates keys, answers {"keys": ["ULTR-..."], "licenses": [...]}; the
                                                   full key is shown only in this answer, the table keeps just its SHA-256
  PATCH /v1/admin/licenses/{id}                 -> edits name, email, phone, note, plan, status, durationDays, expiresAt
  POST  /v1/admin/licenses/{id}/allow-move      -> lets the key move to another phone right now (skips the one-day wait)

{id} is the key's SHA-256, the same value as "id" in the list. Items are the ones mcp/license reads and writes.

Env vars: TABLE, ADMIN_EMAILS (comma separated; empty locks everyone out).
"""
import base64
import hashlib
import json
import os
import re
import secrets
import time

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
MAX_CREATE = 50
MAX_DAYS = 3650
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PHONE_RE = re.compile(r"^[0-9+()\-\s.]{3,30}$")


# Text fields an admin can set, with their maximum length.
TEXT_FIELDS = {"name": 80, "email": 120, "phone": 30, "note": 300, "plan": 40}

_table = None


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


def create(event, now):
    data = _json_body(event)
    if data is None:
        return _resp(400, {"error": "bad_json"})
    fields = {}
    for field in ("name", "email", "phone", "note"):
        value, error = _clean_text(data, field)
        if error:
            return _resp(400, {"error": error})
        if value:
            fields[field] = value
    plan, error = _clean_text({"plan": data.get("plan", "yearly")}, "plan")
    if error:
        return _resp(400, {"error": error})
    days, error = _clean_int(data, "durationDays", MAX_DAYS)
    if error:
        return _resp(400, {"error": error})
    count, error = _clean_int(data, "count", MAX_CREATE)
    if error or count == 0:
        return _resp(400, {"error": "bad_count"})
    days = 365 if days is None else days
    count = count or 1

    keys, created = [], []
    for _ in range(count):
        key = new_key()
        item = {"keyHash": key_hash(key), "status": "active", "plan": plan, "durationDays": days, "expiresAt": 0,
                "createdAt": now, "hint": key[-4:], **fields}
        _db().put_item(Item=item, ConditionExpression="attribute_not_exists(keyHash)")
        keys.append(key)
        created.append(_public(item, now))
    return _resp(200, {"keys": keys, "licenses": created})


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
            changes[field] = value
    if "status" in data:
        if data["status"] not in ("active", "revoked"):
            return _resp(400, {"error": "bad_status"})
        changes["status"] = data["status"]
    for field, high in (("durationDays", MAX_DAYS), ("expiresAt", 4_102_444_800)):
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
    match = re.fullmatch(r"/v1/admin/licenses/([0-9a-f]{64})(/allow-move)?", path)
    if match:
        license_id, suffix = match.groups()
        if method == "PATCH" and not suffix:
            return update(event, now, license_id)
        if method == "POST" and suffix:
            return allow_move(event, now, license_id)
    return _resp(404, {"error": "not found"})
