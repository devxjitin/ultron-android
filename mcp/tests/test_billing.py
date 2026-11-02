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

    def scan(self, **kwargs):
        return {"Items": [dict(i) for i in self.items.values()]}


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

    def test_status_rereads_razorpay_when_the_paid_cycle_ended_long_ago(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1", current_end=int(time.time()) - 5 * 86_400)
        self.webhook(self.event_for("sub_1"))
        # The halted event never arrived; Razorpay itself already knows.
        self.pay("sub_1", status="halted", current_end=int(time.time()) - 5 * 86_400)
        status = call(request("GET", "/v1/billing/status"))[1]
        self.assertFalse(status["active"])
        self.assertEqual("halted", status["status"])

    def test_status_keeps_a_paying_user_active_when_razorpay_is_down(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1", current_end=int(time.time()) - 5 * 86_400)
        self.webhook(self.event_for("sub_1"))

        def down(method, path, payload=None):
            raise billing.urllib.error.URLError("down")
        billing._razorpay = down
        self.assertTrue(call(request("GET", "/v1/billing/status"))[1]["active"])

    def test_subscribing_twice_for_the_same_plan_reuses_the_unpaid_subscription(self):
        first = call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))[1]
        second = call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))[1]
        self.assertEqual(first["subscriptionId"], second["subscriptionId"])
        self.assertEqual(1, len(self.razorpay.subscriptions))

    def test_subscribing_notices_a_payment_whose_webhook_was_missed(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1")
        self.assertEqual(409, call(request("POST", "/v1/billing/subscribe", {"plan": "yearly"}))[0])

    def test_pending_leftover_is_cancelled_before_a_new_subscription(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.razorpay.subscriptions["sub_1"].update(status="pending", current_end=0)
        self.razorpay.cancelled = []
        original = self.razorpay.__call__

        def with_cancel(method, path, payload=None):
            if path.endswith("/cancel"):
                self.razorpay.cancelled.append(path)
                return {}
            return original(method, path, payload)
        billing._razorpay = with_cancel
        self.assertEqual(200, call(request("POST", "/v1/billing/subscribe", {"plan": "yearly"}))[0])
        self.assertEqual(["/subscriptions/sub_1/cancel"], self.razorpay.cancelled)

    def test_payment_on_an_abandoned_older_try_still_unlocks(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        # A second subscription replaced the first in the table, but the user paid the first one.
        self.razorpay.subscriptions["sub_2"] = {"id": "sub_2", "status": "created", "current_end": None,
                                                "notes": {"uid": "user-1", "plan": "yearly"}}
        billing._record("user-1", self.razorpay.subscriptions["sub_2"])
        self.pay("sub_1")
        self.webhook(self.event_for("sub_1"))
        status = call(request("GET", "/v1/billing/status"))[1]
        self.assertTrue(status["active"])
        self.assertEqual("monthly", status["plan"])

    def test_missing_secrets_refuse_instead_of_signing_with_an_empty_key(self):
        billing._secrets = {"key_id": "rzp_test_key"}
        raw = json.dumps(self.event_for("sub_1"))
        empty_sig = hmac.new(b"", raw.encode(), hashlib.sha256).hexdigest()
        code, _ = call(request("POST", "/v1/billing/webhook", uid="", raw=raw, headers={"X-Razorpay-Signature": empty_sig}))
        self.assertEqual(503, code)
        forged = {"paymentId": "p", "subscriptionId": "sub_1",
                  "signature": hmac.new(b"", b"p|sub_1", hashlib.sha256).hexdigest()}
        self.assertEqual(503, call(request("POST", "/v1/billing/verify", forged))[0])

    def test_network_failure_to_razorpay_is_a_502(self):
        def down(method, path, payload=None):
            raise billing.urllib.error.URLError("timeout")
        billing._razorpay = down
        self.assertEqual(502, call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))[0])

    def test_daily_sweep_locks_users_whose_cancel_webhook_never_came(self):
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}))
        self.pay("sub_1", current_end=int(time.time()) - 5 * 86_400)
        self.webhook(self.event_for("sub_1"))
        call(request("POST", "/v1/billing/subscribe", {"plan": "monthly"}, uid="fresh"))
        self.pay("sub_2")
        self.webhook(self.event_for("sub_2"))
        self.pay("sub_1", status="halted", current_end=int(time.time()) - 5 * 86_400)
        result = billing.lambda_handler({"source": "aws.events"}, None)
        self.assertEqual({"reconciled": True}, result)
        self.assertEqual("halted", self.table.items["user-1"]["status"])
        self.assertEqual("active", self.table.items["fresh"]["status"])

    def test_unknown_route_is_not_found(self):
        self.assertEqual(404, call(request("GET", "/v1/billing/nope"))[0])


if __name__ == "__main__":
    unittest.main()
