"""Offline tests for the billing Lambda. DynamoDB, SSM and Razorpay are replaced by fakes.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import hashlib
import hmac
import importlib.util
import json
import os
import pathlib
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.environ.update({"TABLE": "subs", "SECRET_PREFIX": "/ultron/razorpay", "PLAN_MONTHLY": "plan_m",
                   "PLAN_HALFYEARLY": "plan_h", "PLAN_YEARLY": "plan_y"})

spec = importlib.util.spec_from_file_location("billing_lambda", ROOT / "billing/lambda_function.py")
billing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(billing)

KEY_SECRET = "key-secret"
WEBHOOK_SECRET = "hook-secret"


class FakeTable:
    def __init__(self):
        self.items = {}

    def get_item(self, Key):
        item = self.items.get(Key["uid"])
        return {"Item": dict(item)} if item else {}

    def put_item(self, Item):
        self.items[Item["uid"]] = dict(Item)


class FakeRazorpay:
    def __init__(self):
        self.subscriptions = {}
        self.calls = []

    def __call__(self, method, path, payload=None):
        self.calls.append((method, path, payload))
        if method == "POST" and path == "/subscriptions":
            sid = f"sub_{len(self.subscriptions) + 1}"
            self.subscriptions[sid] = {"id": sid, "status": "created", "current_end": None, "notes": payload["notes"]}
            return dict(self.subscriptions[sid])
        if method == "GET" and path.startswith("/subscriptions/"):
            return dict(self.subscriptions[path.rsplit("/", 1)[1]])
        raise AssertionError(f"unexpected call {method} {path}")


def request(method, path, body=None, uid="user-1", provider="google.com", headers=None, raw=None):
    claims = {"sub": uid, "firebase": json.dumps({"sign_in_provider": provider})} if uid else {}
    return {
        "rawPath": path,
        "requestContext": {"http": {"method": method}, "authorizer": {"jwt": {"claims": claims}}},
        "headers": headers or {},
        "body": raw if raw is not None else (json.dumps(body) if body is not None else ""),
        "isBase64Encoded": False,
    }


def call(event):
    response = billing.lambda_handler(event, None)
    return response["statusCode"], json.loads(response["body"])


class BillingTests(unittest.TestCase):
    def setUp(self):
        self.table = FakeTable()
        self.razorpay = FakeRazorpay()
        billing._table = self.table
        billing._secrets = {"key_id": "rzp_test_key", "key_secret": KEY_SECRET, "webhook_secret": WEBHOOK_SECRET}
        billing._razorpay = self.razorpay

    def pay(self, sid, status="active", current_end=None):
        self.razorpay.subscriptions[sid].update(status=status, current_end=current_end or int(time.time()) + 86_400)

    def verify_body(self, sid, payment="pay_1"):
        signature = hmac.new(KEY_SECRET.encode(), f"{payment}|{sid}".encode(), hashlib.sha256).hexdigest()
        return {"paymentId": payment, "subscriptionId": sid, "signature": signature}

    def test_new_user_is_not_active(self):
        self.assertEqual((200, {"active": False, "plan": "", "status": "none", "currentEnd": 0}),
                         call(request("GET", "/v1/billing/status")))

    def test_calls_without_a_user_are_refused(self):
        self.assertEqual(401, call(request("GET", "/v1/billing/status", uid=""))[0])

    def test_anonymous_users_cannot_subscribe(self):
        code, _ = call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}, provider="anonymous"))
        self.assertEqual(403, code)
        self.assertEqual([], self.razorpay.calls)

    def test_unknown_plan_is_rejected(self):
        self.assertEqual(400, call(request("POST", "/v1/billing/subscribe", {"plan": "weekly"}))[0])

    def test_subscribe_creates_a_subscription_tagged_with_the_user(self):
        code, body = call(request("POST", "/v1/billing/subscribe", {"plan": "yearly"}))
        self.assertEqual(200, code)
        self.assertEqual({"subscriptionId": "sub_1", "keyId": "rzp_test_key"}, body)
        _, _, payload = self.razorpay.calls[0]
        self.assertEqual("plan_y", payload["plan_id"])
        self.assertEqual({"uid": "user-1", "plan": "yearly"}, payload["notes"])
        self.assertFalse(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_map_style_firebase_claim_counts_as_google(self):
        event = request("POST", "/v1/billing/subscribe", {"plan": "monthly"})
        event["requestContext"]["authorizer"]["jwt"]["claims"]["firebase"] = "map[identities:map[] sign_in_provider:google.com]"
        self.assertEqual(200, call(event)[0])

    def test_verify_with_a_good_signature_activates(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1")
        code, body = call(request("POST", "/v1/billing/verify", self.verify_body("sub_1")))
        self.assertEqual(200, code)
        self.assertTrue(body["active"])
        self.assertEqual("monthly", body["plan"])

    def test_verify_with_a_forged_signature_changes_nothing(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1")
        forged = dict(self.verify_body("sub_1"), signature="0" * 64)
        self.assertEqual(400, call(request("POST", "/v1/billing/verify", forged))[0])
        self.assertFalse(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_verify_trusts_razorpay_status_not_the_request(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        # Signed correctly, but Razorpay still says the payment has not gone through.
        code, body = call(request("POST", "/v1/billing/verify", self.verify_body("sub_1")))
        self.assertEqual(200, code)
        self.assertFalse(body["active"])

    def test_verify_of_someone_elses_subscription_is_refused(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}, uid="other"))
        self.pay("sub_1")
        self.assertEqual(403, call(request("POST", "/v1/billing/verify", self.verify_body("sub_1")))[0])
        self.assertNotIn("user-1", self.table.items)

    def test_active_users_cannot_subscribe_twice(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1")
        call(request("POST", "/v1/billing/verify", self.verify_body("sub_1")))
        self.assertEqual(409, call(request("POST", "/v1/billing/subscribe", {"plan": "yearly"}))[0])

    def webhook(self, payload, secret=WEBHOOK_SECRET):
        raw = json.dumps(payload)
        signature = hmac.new(secret.encode(), raw.encode(), hashlib.sha256).hexdigest()
        return call(request("POST", "/v1/billing/webhook", uid="", raw=raw, headers={"X-Razorpay-Signature": signature}))

    def event_for(self, sid, name="subscription.charged"):
        return {"event": name, "payload": {"subscription": {"entity": {"id": sid}}}}

    def test_webhook_with_a_bad_signature_is_rejected(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1")
        self.assertEqual(400, self.webhook(self.event_for("sub_1"), secret="wrong")[0])
        self.assertFalse(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_charged_webhook_activates_and_halted_one_locks(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "halfyearly"}))
        self.pay("sub_1")
        self.assertEqual(200, self.webhook(self.event_for("sub_1"))[0])
        self.assertTrue(call(request("GET", "/v1/billing/status"))[1]["active"])
        self.pay("sub_1", status="halted")
        self.webhook(self.event_for("sub_1", "subscription.halted"))
        self.assertFalse(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_cancelled_plan_stays_usable_until_the_paid_cycle_ends(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1", status="cancelled", current_end=int(time.time()) + 3_600)
        self.webhook(self.event_for("sub_1", "subscription.cancelled"))
        self.assertTrue(call(request("GET", "/v1/billing/status"))[1]["active"])
        self.pay("sub_1", status="cancelled", current_end=int(time.time()) - 1)
        self.webhook(self.event_for("sub_1", "subscription.cancelled"))
        self.assertFalse(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_old_subscription_webhook_does_not_overwrite_the_new_one(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1", status="cancelled", current_end=int(time.time()) - 1)
        self.webhook(self.event_for("sub_1", "subscription.cancelled"))
        call(request("POST", "/v1/billing/subscribe", {"plan": "yearly"}))
        self.pay("sub_2")
        self.webhook(self.event_for("sub_2"))
        self.webhook(self.event_for("sub_1", "subscription.cancelled"))
        status = call(request("GET", "/v1/billing/status"))[1]
        self.assertTrue(status["active"])
        self.assertEqual("yearly", status["plan"])

    def test_unrelated_webhook_events_are_ignored(self):
        self.assertEqual((200, {"ignored": True}), self.webhook({"event": "payment.captured", "payload": {}}))

    def test_unknown_route_is_not_found(self):
        self.assertEqual(404, call(request("GET", "/v1/billing/nope"))[0])


if __name__ == "__main__":
    unittest.main()
