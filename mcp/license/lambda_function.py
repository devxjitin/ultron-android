"""Activation keys for the Ultron app. Keys are sold on the website store; the app only checks them here.

Routes (behind the Ultron HTTP API; each carries a Firebase ID token that API Gateway already checked):
  POST /v1/license/activate  {"key"} -> binds the key to the caller's device (moving it off any other) and returns status
  POST /v1/license/status    {"key"} -> status for the caller without moving the key

Both answer 200 with {"active", "plan", "expiresAt", "reason"}; "reason" says why a key is not active (unknown_key,
expired, revoked, moved, moved_recently). Only malformed requests get a 4xx. For a key that exists the answer also carries
the buyer details the admin entered in Ultronapp: "name", "email", "phone", "activatedAt", "durationDays" (empty or 0 when
not set). Only the holder of the key can ask, so these are not secret from them.

One DynamoDB item per key, keyed by the SHA-256 of the normalized key so the table never holds a usable key:
  keyHash      S  primary key
  status       S  "active" or "revoked"
  plan         S  label from the store, such as "monthly" or "yearly"
  durationDays N  how long the key runs from its first activation; 0 never expires
  expiresAt    N  epoch seconds, set on first activation (or up front by the store); 0 never expires
  uid          S  Firebase uid of the device the key is on, absent until activated
  activatedAt  N  first activation
  movedAt      N  last time the key moved to another device
  name, email, phone S  buyer details, optional (written by the store, Ultronapp or scripts/create-license-key.py)
The store (or Ultronapp, or scripts/create-license-key.py) writes the first four and the buyer details; this Lambda writes
the rest.

Env vars: TABLE.
"""
import base64
import hashlib
import json
import os
import re
import time

PREFIX = "ULTR"
ALPHABET = set("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
# A key may move to another device (new phone, reinstall) at most once a day, so one key can't be shared around.
MOVE_COOLDOWN_SECONDS = 86_400

_table = None


def _db():
    global _table
    if _table is None:
        import boto3  # Lambda runtime has it; imported here so the offline tests run without it.
        _table = boto3.resource("dynamodb").Table(os.environ["TABLE"])
    return _table


def normalize(raw):
    """Same rules as LicenseKey.normalize in the app: ULTR- plus 16 Crockford base32 characters, or None."""
    body = re.sub(r"[^0-9A-Za-z]", "", str(raw or "")).upper()
    if body.startswith(PREFIX):
        body = body[len(PREFIX):]
    body = body.replace("O", "0").replace("I", "1").replace("L", "1")
    if len(body) != 16 or any(c not in ALPHABET for c in body):
        return None
    return "-".join([PREFIX] + [body[i:i + 4] for i in range(0, 16, 4)])


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


def _uid(event):
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    return claims.get("sub") or ""


def _answer(item, active, reason=""):
    body = {
        "active": active,
        "plan": str(item.get("plan", "")) if item else "",
        "expiresAt": int(item.get("expiresAt", 0)) if item else 0,
        "reason": reason,
    }
    # Buyer details only go to the phone that holds an active key, never in a moved/revoked/expired answer.
    if item and active:
        body.update({
            "name": str(item.get("name", "")),
            "email": str(item.get("email", "")),
            "phone": str(item.get("phone", "")),
            "activatedAt": int(item.get("activatedAt", 0)),
            "durationDays": int(item.get("durationDays", 0)),
        })
    return _resp(200, body)


def _unusable_reason(item, now):
    """Why [item] can't be used by anyone right now, or "" when it can."""
    if not item:
        return "unknown_key"
    if item.get("status") != "active":
        return "revoked"
    expires = int(item.get("expiresAt", 0))
    if expires and expires <= now:
        return "expired"
    return ""


def _load(event):
    data = _json_body(event)
    if data is None:
        return None, None
    key = normalize(data.get("key"))
    if key is None:
        return None, None
    found = _db().get_item(Key={"keyHash": key_hash(key)}).get("Item")
    return key, found


def status(event):
    key, item = _load(event)
    if key is None:
        return _resp(400, {"error": "bad_key"})
    now = int(time.time())
    reason = _unusable_reason(item, now)
    if reason:
        return _answer(item, False, reason)
    if item.get("uid") != _uid(event):
        return _answer(item, False, "moved")
    return _answer(item, True)


def activate(event):
    key, item = _load(event)
    if key is None:
        return _resp(400, {"error": "bad_key"})
    now = int(time.time())
    reason = _unusable_reason(item, now)
    if reason:
        return _answer(item, False, reason)
    uid = _uid(event)
    previous = item.get("uid")
    if previous == uid:
        return _answer(item, True)
    if previous and now - int(item.get("movedAt", 0)) < MOVE_COOLDOWN_SECONDS:
        return _answer(item, False, "moved_recently")

    names = {"#uid": "uid"}
    values = {":uid": uid, ":now": now}
    sets = ["#uid = :uid"]
    if previous:
        sets.append("movedAt = :now")
    else:
        sets.append("activatedAt = :now")
        days = int(item.get("durationDays", 0))
        if days and not int(item.get("expiresAt", 0)):
            values[":exp"] = now + days * 86_400
            sets.append("expiresAt = :exp")
    if previous:
        condition = "#uid = :prev"
        values[":prev"] = previous
    else:
        condition = "attribute_not_exists(#uid)"
    # The key must still be active and unexpired at write time, so a revoke or expiry edit that lands between the
    # read above and this write cannot be answered with active=true.
    condition += " AND #status = :active AND (attribute_not_exists(expiresAt) OR expiresAt = :zero OR expiresAt > :now)"
    names["#status"] = "status"
    values[":active"] = "active"
    values[":zero"] = 0
    try:
        updated = _db().update_item(
            Key={"keyHash": key_hash(key)},
            UpdateExpression="SET " + ", ".join(sets),
            ConditionExpression=condition,
            ExpressionAttributeNames=names,
            ExpressionAttributeValues=values,
            ReturnValues="ALL_NEW",
        )["Attributes"]
    except Exception as e:  # ConditionalCheckFailedException: another device took the key, or it was revoked or expired.
        if "ConditionalCheckFailed" in type(e).__name__ or "ConditionalCheckFailed" in str(e):
            fresh = _db().get_item(Key={"keyHash": key_hash(key)}, ConsistentRead=True).get("Item")
            return _answer(fresh, False, _unusable_reason(fresh, now) or "moved_recently")
        raise
    return _answer(updated, True)


ROUTES = {
    ("POST", "/v1/license/activate"): activate,
    ("POST", "/v1/license/status"): status,
}


def lambda_handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "")
    handler = ROUTES.get((method, event.get("rawPath", "")))
    if handler is None:
        return _resp(404, {"error": "not found"})
    if not _uid(event):
        return _resp(401, {"error": "sign in required"})
    return handler(event)
