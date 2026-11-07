"""Checks the Firebase App Check token a request carries in `X-Firebase-AppCheck`, using only the standard library.

An App Check token is an RS256 JWT signed by Google. This module fetches Google's public keys, verifies the signature
(RSASSA-PKCS1-v1_5 with SHA-256), then the issuer, audience and expiry. A copy of this file sits next to every Lambda that
uses it (mcp/web-extract, mcp/ultron-updates, mcp/feature-requests); mcp/tests keeps them identical.

Env vars:
  FIREBASE_PROJECT_NUMBER  the numeric project number (not the project id); blank turns the check off
  APP_CHECK_MODE           "log" (default): record a missing or bad token but let the request through
                           "enforce": refuse it with 401   |   "off": do nothing
"""
import base64
import hashlib
import json
import os
import time
import urllib.request

JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks"
JWKS_TTL_SECONDS = 3600
# DER prefix of a SHA-256 DigestInfo, the fixed part of an RSASSA-PKCS1-v1_5 signature.
SHA256_PREFIX = bytes.fromhex("3031300d060960864801650304020105000420")

# Google's key server is asked again at most this often for a key id it does not know, so a stream of made-up ids cannot
# make every request wait on it.
MIN_REFETCH_SECONDS = 60

_keys = {"fetched": 0.0, "tried": 0.0, "by_kid": {}}


def _b64(data):
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4))


def _fetch_keys():
    with urllib.request.urlopen(JWKS_URL, timeout=5) as response:
        keys = json.loads(response.read())["keys"]
    _keys["by_kid"] = {k["kid"]: k for k in keys if k.get("kty") == "RSA"}
    _keys["fetched"] = time.time()


def _key_for(kid):
    now = time.time()
    stale = now - _keys["fetched"] > JWKS_TTL_SECONDS
    # An unknown kid also refetches, in case Google rotated its keys. Neither refetches within a minute of the last try,
    # which counts even when it failed, so an unreachable key server is not asked again on every request.
    wanted = stale or kid not in _keys["by_kid"]
    if wanted and now - _keys.get("tried", 0.0) > MIN_REFETCH_SECONDS:
        _keys["tried"] = now
        _fetch_keys()
    return _keys["by_kid"].get(kid)


def _signature_ok(key, signing_input, signature):
    n, e = int.from_bytes(_b64(key["n"]), "big"), int.from_bytes(_b64(key["e"]), "big")
    size = (n.bit_length() + 7) // 8
    if len(signature) != size:
        return False
    decoded = pow(int.from_bytes(signature, "big"), e, n).to_bytes(size, "big")
    tail = SHA256_PREFIX + hashlib.sha256(signing_input).digest()
    expected = b"\x00\x01" + b"\xff" * (size - len(tail) - 3) + b"\x00" + tail
    # Constant-time compare is not needed: the signature is public input, nothing secret is being matched.
    return size >= len(tail) + 11 and decoded == expected


def verify(token, project_number, now=None):
    """Returns None when [token] is a valid App Check token for the project, else a short reason."""
    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
        header = json.loads(_b64(header_b64))
        claims = json.loads(_b64(payload_b64))
        if header.get("alg") != "RS256":
            return "unexpected algorithm"
        key = _key_for(header.get("kid", ""))
        if key is None:
            return "unknown signing key"
        if not _signature_ok(key, f"{header_b64}.{payload_b64}".encode(), _b64(signature_b64)):
            return "bad signature"
    except Exception as e:  # malformed token or an unreachable key server: never crash the request
        return f"unreadable token ({type(e).__name__})"
    if claims.get("iss") != f"https://firebaseappcheck.googleapis.com/{project_number}":
        return "wrong issuer"
    audience = claims.get("aud")
    if f"projects/{project_number}" not in (audience if isinstance(audience, list) else [audience]):
        return "wrong audience"
    if not claims.get("sub"):
        return "no app id"
    if float(claims.get("exp", 0)) <= (now if now is not None else time.time()):
        return "expired"
    return None


def check(event):
    """None when the request may continue, else a 401 response dict (only in enforce mode). Logs every problem."""
    mode = os.environ.get("APP_CHECK_MODE", "log")
    project = os.environ.get("FIREBASE_PROJECT_NUMBER", "")
    if mode == "off" or not project:
        return None
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    token = headers.get("x-firebase-appcheck", "")
    problem = verify(token, project) if token else "missing token"
    if problem is None:
        return None
    print(f"App Check {problem} (mode={mode}, path={event.get('rawPath')})")
    if mode != "enforce":
        return None
    return {"statusCode": 401, "headers": {"content-type": "application/json"},
            "body": json.dumps({"error": "app check failed"})}
