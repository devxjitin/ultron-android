"""Offline tests for the App Check verifier. A throwaway 1024-bit RSA key (test only) signs the tokens; Google's key
server is replaced by a fake.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import base64
import hashlib
import importlib.util
import json
import pathlib
import sys
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("appcheck", ROOT / "web-extract/appcheck.py")
appcheck = importlib.util.module_from_spec(spec)
spec.loader.exec_module(appcheck)

N = 0x4dc92393578cdd278e6dbe30e97b46cb045806c01b07fdcf071b07a013d3e451f9339825af211825d4cf4648f88fce9737f56a7b51e265cda592e38f83182012f9a105abecefea9619e498e37b3d7458eea10a989eee510680d17888d6a102af4733b77fce6875b0bc62a1a1b63c6aa3591dfdfe88131e3e520e3d5f210c8197
D = 0x16f4ae40148d68d2fcb09c7978de3dc53a51f4240b962f2c42ef922f86ca78ce4c5a12a3c46c62e0ff4df073268fe22b348383da430f2a76275013424e30f77deecb2b66b8a8f4ba53a7a9451d1d4d6e2376f69d8b1eca23fd21f1792695a6ebf1a7e5db2d4ff9480b99059a368b17175cb5a708e709944dbc8826db740fd521
E = 65537
PROJECT = "434457544612"


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def int_b64(value):
    return b64(value.to_bytes((value.bit_length() + 7) // 8, "big"))


def token(claims=None, kid="k1", alg="RS256", tamper=False):
    body = {"iss": f"https://firebaseappcheck.googleapis.com/{PROJECT}", "aud": [f"projects/{PROJECT}"],
            "sub": "1:434457544612:android:abc", "exp": time.time() + 600}
    body.update(claims or {})
    header_b64 = b64(json.dumps({"alg": alg, "kid": kid, "typ": "JWT"}).encode())
    payload_b64 = b64(json.dumps(body).encode())
    signing_input = f"{header_b64}.{payload_b64}".encode()
    size = (N.bit_length() + 7) // 8
    tail = appcheck.SHA256_PREFIX + hashlib.sha256(signing_input).digest()
    padded = b"\x00\x01" + b"\xff" * (size - len(tail) - 3) + b"\x00" + tail
    signature = pow(int.from_bytes(padded, "big"), D, N).to_bytes(size, "big")
    if tamper:
        signature = bytes([signature[0] ^ 1]) + signature[1:]
    return f"{header_b64}.{payload_b64}.{b64(signature)}"


class VerifyTests(unittest.TestCase):
    def setUp(self):
        self.fetches = 0

        def fake_fetch():
            self.fetches += 1
            appcheck._keys["by_kid"] = {"k1": {"kty": "RSA", "kid": "k1", "n": int_b64(N), "e": int_b64(E)}}
            appcheck._keys["fetched"] = time.time()
        appcheck._fetch_keys = fake_fetch
        appcheck._keys.update(fetched=0.0, tried=0.0, by_kid={})

    def test_valid_token_passes(self):
        self.assertIsNone(appcheck.verify(token(), PROJECT))

    def test_keys_are_cached_between_calls(self):
        appcheck.verify(token(), PROJECT)
        appcheck.verify(token(), PROJECT)
        self.assertEqual(1, self.fetches)

    def test_unknown_key_ids_do_not_refetch_more_than_once_a_minute(self):
        appcheck.verify(token(), PROJECT)
        for _ in range(3):
            self.assertEqual("unknown signing key", appcheck.verify(token(kid="made-up"), PROJECT))
        self.assertEqual(1, self.fetches)  # the first call fetched; the made-up ids found a recent try and did not
        appcheck._keys["tried"] -= appcheck.MIN_REFETCH_SECONDS + 1
        self.assertEqual("unknown signing key", appcheck.verify(token(kid="made-up"), PROJECT))
        self.assertEqual(2, self.fetches)

    def test_a_failed_fetch_is_not_retried_on_every_request(self):
        calls = []

        def failing_fetch():
            calls.append(1)
            raise OSError("key server unreachable")
        appcheck._fetch_keys = failing_fetch
        self.assertEqual("unreadable token (OSError)", appcheck.verify(token(), PROJECT))
        for _ in range(2):
            self.assertEqual("unknown signing key", appcheck.verify(token(), PROJECT))
        self.assertEqual(1, len(calls))

    def test_tampered_signature_is_rejected(self):
        self.assertEqual("bad signature", appcheck.verify(token(tamper=True), PROJECT))

    def test_changed_payload_invalidates_signature(self):
        head, _, sig = token().split(".")
        forged = b64(json.dumps({"iss": f"https://firebaseappcheck.googleapis.com/{PROJECT}",
                                 "aud": [f"projects/{PROJECT}"], "sub": "evil", "exp": time.time() + 600}).encode())
        self.assertEqual("bad signature", appcheck.verify(f"{head}.{forged}.{sig}", PROJECT))

    def test_other_projects_token_is_rejected(self):
        self.assertEqual("wrong issuer", appcheck.verify(token(), "999"))
        self.assertEqual("wrong audience", appcheck.verify(token({"aud": ["projects/999"]}), PROJECT))

    def test_expired_token_is_rejected(self):
        self.assertEqual("expired", appcheck.verify(token({"exp": time.time() - 1}), PROJECT))

    def test_non_rs256_and_unknown_key_are_rejected(self):
        self.assertEqual("unexpected algorithm", appcheck.verify(token(alg="none"), PROJECT))
        self.assertEqual("unknown signing key", appcheck.verify(token(kid="other"), PROJECT))

    def test_garbage_never_raises(self):
        for junk in ("", "a.b", "a.b.c", "not a token at all"):
            self.assertIsNotNone(appcheck.verify(junk, PROJECT))


class CheckTests(unittest.TestCase):
    def setUp(self):
        VerifyTests.setUp(self)
        appcheck.os.environ["FIREBASE_PROJECT_NUMBER"] = PROJECT

    def tearDown(self):
        for name in ("FIREBASE_PROJECT_NUMBER", "APP_CHECK_MODE"):
            appcheck.os.environ.pop(name, None)

    def event(self, tok=None):
        return {"rawPath": "/v1/web/mcp", "headers": {"X-Firebase-AppCheck": tok} if tok else {}}

    def test_log_mode_lets_missing_and_bad_tokens_through(self):
        self.assertIsNone(appcheck.check(self.event()))
        self.assertIsNone(appcheck.check(self.event(token(tamper=True))))

    def test_enforce_mode_refuses_missing_and_bad_tokens(self):
        appcheck.os.environ["APP_CHECK_MODE"] = "enforce"
        self.assertEqual(401, appcheck.check(self.event())["statusCode"])
        self.assertEqual(401, appcheck.check(self.event(token(tamper=True)))["statusCode"])
        self.assertIsNone(appcheck.check(self.event(token())))

    def test_off_or_no_project_skips_the_check(self):
        appcheck.os.environ["APP_CHECK_MODE"] = "off"
        self.assertIsNone(appcheck.check(self.event()))
        appcheck.os.environ["APP_CHECK_MODE"] = "enforce"
        del appcheck.os.environ["FIREBASE_PROJECT_NUMBER"]
        self.assertIsNone(appcheck.check(self.event()))

    def test_web_extract_refuses_in_enforce_mode_when_behind_the_api(self):
        sys.path.insert(0, str(ROOT / "web-extract"))
        web_spec = importlib.util.spec_from_file_location("web_for_appcheck", ROOT / "web-extract/lambda_function.py")
        web = importlib.util.module_from_spec(web_spec)
        web_spec.loader.exec_module(web)
        web.appcheck._fetch_keys = appcheck._fetch_keys
        appcheck.os.environ.update(JWT_AUTH="1", APP_CHECK_MODE="enforce")
        try:
            event = {"rawPath": "/v1/web/mcp", "requestContext": {"http": {"method": "POST"}}, "headers": {}, "body": "{}"}
            self.assertEqual(401, web.lambda_handler(event, None)["statusCode"])
        finally:
            appcheck.os.environ.pop("JWT_AUTH", None)

    def test_lambda_copies_are_identical(self):
        original = (ROOT / "web-extract/appcheck.py").read_bytes()
        for folder in ("ultron-updates", "feature-requests"):
            self.assertEqual(original, (ROOT / folder / "appcheck.py").read_bytes(), folder)


if __name__ == "__main__":
    unittest.main()
