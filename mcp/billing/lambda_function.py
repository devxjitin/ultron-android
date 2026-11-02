"""Razorpay subscriptions for the Ultron app. One record per Firebase user in DynamoDB says whether they have paid.

Routes (behind the Ultron HTTP API; all but the webhook carry a Firebase ID token that API Gateway already checked):
  GET  /v1/billing/status     -> {"active", "plan", "status", "currentEnd"} for the caller
  POST /v1/billing/subscribe  {"plan": "monthly" | "halfyearly" | "yearly"} -> {"subscriptionId", "keyId"} for Checkout
  POST /v1/billing/verify     {"paymentId", "subscriptionId", "signature"} -> status, after the Checkout signature checks out
  POST /v1/billing/webhook    Razorpay events, signed with the webhook secret (no Firebase token)

Razorpay is the source of truth: verify and the webhook both re-read the subscription from Razorpay before writing, so a
forged request body can never mark anyone as paid.

Env vars: TABLE, PLAN_MONTHLY, PLAN_HALFYEARLY, PLAN_YEARLY (Razorpay plan ids), SECRET_PREFIX (SSM path that holds
key_id, key_secret and webhook_secret as SecureString parameters).
"""
import base64
import hashlib
import hmac
import json
import os
import re
import time
import urllib.error
import urllib.request

RAZORPAY_API = "https://api.razorpay.com/v1"
# How many billing cycles a subscription runs before Razorpay ends it (Razorpay needs a number): about ten years.
PLANS = {
    "monthly": {"env": "PLAN_MONTHLY", "total_count": 120},
    "halfyearly": {"env": "PLAN_HALFYEARLY", "total_count": 20},
    "yearly": {"env": "PLAN_YEARLY", "total_count": 10},
}
# Razorpay statuses that mean the user has paid for the current cycle.
PAID_STATUSES = {"active", "authenticated"}
# Statuses after which a new subscription may be started.
FINISHED_STATUSES = {"cancelled", "completed", "expired", "halted"}
SUBSCRIPTION_EVENTS = {
    "subscription.authenticated", "subscription.activated", "subscription.charged", "subscription.pending",
    "subscription.halted", "subscription.cancelled", "subscription.completed", "subscription.paused",
    "subscription.resumed", "subscription.updated",
}

_table = None
_secrets = None


def _db():
    global _table
    if _table is None:
        import boto3  # Lambda runtime has it; imported here so the offline tests run without it.
        _table = boto3.resource("dynamodb").Table(os.environ["TABLE"])
    return _table


def _secret(name):
    """key_id, key_secret or webhook_secret, read once per container from SSM."""
    global _secrets
    if _secrets is None:
        prefix = os.environ["SECRET_PREFIX"].rstrip("/")
        names = [f"{prefix}/{n}" for n in ("key_id", "key_secret", "webhook_secret")]
        import boto3
        found = boto3.client("ssm").get_parameters(Names=names, WithDecryption=True)["Parameters"]
        _secrets = {p["Name"].rsplit("/", 1)[1]: p["Value"] for p in found}
    return _secrets.get(name, "")


def _resp(status, body):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(body)}


def _raw_body(event):
    body = event.get("body") or ""
    return base64.b64decode(body) if event.get("isBase64Encoded") else body.encode()


def _json_body(event):
    try:
        data = json.loads(_raw_body(event) or b"{}")
    except ValueError:
        return None
    return data if isinstance(data, dict) else None


def _uid(event):
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    return claims.get("sub") or ""


def _signed_in_with_google(event):
    """Anonymous Firebase users get a new uid on reinstall, so only a real account may buy a plan."""
    claims = event.get("requestContext", {}).get("authorizer", {}).get("jwt", {}).get("claims", {})
    firebase = claims.get("firebase")
    if isinstance(firebase, dict):
        return firebase.get("sign_in_provider") == "google.com"
    # API Gateway hands nested claims over as one string (JSON or "map[k:v ...]"), so match the field in the text.
    return bool(re.search(r"sign_in_provider\W+google\.com", str(firebase or "")))


def _razorpay(method, path, payload=None):
    auth = base64.b64encode(f"{_secret('key_id')}:{_secret('key_secret')}".encode()).decode()
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(RAZORPAY_API + path, data=data, method=method, headers={
        "Authorization": f"Basic {auth}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read())


def _record(uid, subscription):
    """Stores what Razorpay says about [subscription] for [uid] and returns the status view."""
    item = {
        "uid": uid,
        "subscriptionId": subscription["id"],
        "plan": (subscription.get("notes") or {}).get("plan", ""),
        "status": subscription.get("status", ""),
        "currentEnd": int(subscription.get("current_end") or 0),
        "updatedAt": int(time.time()),
    }
    _db().put_item(Item=item)
    return _view(item)


def _view(item):
    if not item:
        return {"active": False, "plan": "", "status": "none", "currentEnd": 0}
    current_end = int(item.get("currentEnd") or 0)
    status = item.get("status", "")
    # A paid cycle stays usable until its end even if the user cancelled in between.
    active = status in PAID_STATUSES or (status == "cancelled" and current_end > time.time())
    return {"active": active, "plan": item.get("plan", ""), "status": status, "currentEnd": current_end}


def _stored(uid):
    return _db().get_item(Key={"uid": uid}).get("Item")


def status(event):
    return _resp(200, _view(_stored(_uid(event))))


def subscribe(event):
    uid = _uid(event)
    if not _signed_in_with_google(event):
        return _resp(403, {"error": "sign in with Google first"})
    body = _json_body(event)
    plan = PLANS.get((body or {}).get("plan", ""))
    if plan is None:
        return _resp(400, {"error": "plan must be monthly, halfyearly or yearly"})
    plan_id = os.environ.get(plan["env"], "")
    if not plan_id:
        return _resp(503, {"error": "plan not set up"})
    current = _stored(uid)
    if current and _view(current)["active"]:
        return _resp(409, {"error": "already subscribed", **_view(current)})
    subscription = _razorpay("POST", "/subscriptions", {
        "plan_id": plan_id,
        "total_count": plan["total_count"],
        "customer_notify": 1,
        "notes": {"uid": uid, "plan": body["plan"]},
    })
    _record(uid, subscription)
    return _resp(200, {"subscriptionId": subscription["id"], "keyId": _secret("key_id")})


def verify(event):
    uid = _uid(event)
    body = _json_body(event) or {}
    payment_id, subscription_id, signature = (str(body.get(k, "")) for k in ("paymentId", "subscriptionId", "signature"))
    if not (payment_id and subscription_id and signature):
        return _resp(400, {"error": "paymentId, subscriptionId and signature are required"})
    # Checkout signs payment_id|subscription_id with the key secret.
    expected = hmac.new(_secret("key_secret").encode(), f"{payment_id}|{subscription_id}".encode(), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, signature):
        return _resp(400, {"error": "signature mismatch"})
    subscription = _razorpay("GET", f"/subscriptions/{subscription_id}")
    if (subscription.get("notes") or {}).get("uid") != uid:
        return _resp(403, {"error": "subscription belongs to another user"})
    return _resp(200, _record(uid, subscription))


def webhook(event):
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    raw = _raw_body(event)
    expected = hmac.new(_secret("webhook_secret").encode(), raw, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, headers.get("x-razorpay-signature", "")):
        return _resp(400, {"error": "bad signature"})
    try:
        payload = json.loads(raw)
    except ValueError:
        return _resp(400, {"error": "bad body"})
    if payload.get("event") not in SUBSCRIPTION_EVENTS:
        return _resp(200, {"ignored": True})
    entity = payload.get("payload", {}).get("subscription", {}).get("entity", {})
    subscription_id = entity.get("id")
    if not subscription_id:
        return _resp(200, {"ignored": True})
    # Events can arrive out of order, so the current state is read back from Razorpay instead of trusting the event.
    subscription = _razorpay("GET", f"/subscriptions/{subscription_id}")
    uid = (subscription.get("notes") or {}).get("uid")
    if not uid:
        return _resp(200, {"ignored": True})
    stored = _stored(uid)
    # A webhook for an older, finished subscription must not overwrite the user's newer one.
    if stored and stored.get("subscriptionId") != subscription_id and stored.get("status") not in FINISHED_STATUSES:
        return _resp(200, {"ignored": True})
    _record(uid, subscription)
    return _resp(200, {"ok": True})


ROUTES = {
    ("GET", "/v1/billing/status"): status,
    ("POST", "/v1/billing/subscribe"): subscribe,
    ("POST", "/v1/billing/verify"): verify,
    ("POST", "/v1/billing/webhook"): webhook,
}


def lambda_handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "")
    handler = ROUTES.get((method, event.get("rawPath", "")))
    if handler is None:
        return _resp(404, {"error": "not found"})
    if handler is not webhook and not _uid(event):
        return _resp(401, {"error": "sign in required"})
    try:
        return handler(event)
    except urllib.error.HTTPError as e:
        print(f"Razorpay error {e.code}: {e.read()[:500]!r}")
        return _resp(502, {"error": "payment provider error"})
