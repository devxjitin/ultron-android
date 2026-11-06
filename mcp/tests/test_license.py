"""Offline tests for the license Lambda. DynamoDB is replaced by a fake.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import importlib.util
import json
import os
import pathlib
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.environ.update({"TABLE": "licenses"})

spec = importlib.util.spec_from_file_location("license_lambda", ROOT / "license/lambda_function.py")
lic = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lic)

KEY = "ULTR-7K2Q-M9XD-4H8P-ZC3N"


class ConditionalCheckFailedException(Exception):
    pass


class FakeTable:
    """Understands only the expressions the Lambda sends: SET a = :v, ... and the two uid conditions."""

    def __init__(self):
        self.items = {}

    def get_item(self, Key, **_):
        item = self.items.get(Key["keyHash"])
        return {"Item": dict(item)} if item else {}

    def update_item(self, Key, UpdateExpression, ConditionExpression, ExpressionAttributeNames,
                    ExpressionAttributeValues, ReturnValues):
        item = self.items[Key["keyHash"]]
        names, values = ExpressionAttributeNames, ExpressionAttributeValues
        if ConditionExpression.startswith("attribute_not_exists(#uid)"):
            ok = "uid" not in item
        else:
            ok = item.get("uid") == values[":prev"]
        if "#status = :active" in ConditionExpression:
            ok = ok and item.get("status") == "active"
            expires = int(item.get("expiresAt", 0))
            ok = ok and (expires == 0 or expires > values[":now"])
        if not ok:
            raise ConditionalCheckFailedException("ConditionalCheckFailed")
        for part in UpdateExpression.removeprefix("SET ").split(", "):
            field, value = part.split(" = ")
            item[names.get(field, field)] = values[value]
        return {"Attributes": dict(item)}


def request(path, key=KEY, uid="device-1", raw=None):
    claims = {"sub": uid} if uid else {}
    return {
        "rawPath": path,
        "requestContext": {"http": {"method": "POST"}, "authorizer": {"jwt": {"claims": claims}}},
        "body": raw if raw is not None else json.dumps({"key": key}),
        "isBase64Encoded": False,
    }


def call(event):
    response = lic.lambda_handler(event, None)
    return response["statusCode"], json.loads(response["body"])


class LicenseTests(unittest.TestCase):
    def setUp(self):
        self.table = FakeTable()
        lic._table = self.table

    def add(self, key=KEY, **fields):
        item = {"keyHash": lic.key_hash(key), "status": "active", "plan": "yearly", "durationDays": 365, "expiresAt": 0}
        item.update(fields)
        self.table.items[item["keyHash"]] = item
        return item

    def test_normalize_accepts_typing_variations(self):
        self.assertEqual(KEY, lic.normalize(" ultr 7k2q-m9xd 4h8p zc3n "))
        self.assertEqual(KEY, lic.normalize("7K2QM9XD4H8PZC3N"))
        self.assertEqual("ULTR-0000-1111-0000-1111", lic.normalize("ULTR-OOOO-IIII-oooo-llll"))
        self.assertIsNone(lic.normalize("ULTR-1234"))
        self.assertIsNone(lic.normalize("ULTR-UUUU-1111-2222-3333"))
        self.assertIsNone(lic.normalize(None))

    def test_calls_without_a_user_are_refused(self):
        self.assertEqual(401, call(request("/v1/license/activate", uid=""))[0])

    def test_malformed_key_is_a_bad_request(self):
        self.assertEqual((400, {"error": "bad_key"}), call(request("/v1/license/activate", key="hello")))
        self.assertEqual(400, call(request("/v1/license/status", raw="not json"))[0])

    def test_unknown_key_is_not_active(self):
        code, body = call(request("/v1/license/activate"))
        self.assertEqual(200, code)
        self.assertEqual({"active": False, "plan": "", "expiresAt": 0, "reason": "unknown_key"}, body)

    def test_inactive_answers_do_not_carry_buyer_details(self):
        self.add(name="Asha", email="asha@example.com", phone="+91 98765 43210", uid="old-phone", movedAt=int(time.time()))
        for path in ("/v1/license/status", "/v1/license/activate"):
            body = call(request(path, uid="stranger"))[1]
            self.assertFalse(body["active"])
            for field in ("name", "email", "phone"):
                self.assertNotIn(field, body)
        self.add(name="Asha", status="revoked")
        self.assertNotIn("name", call(request("/v1/license/activate"))[1])

    def test_answer_carries_the_buyer_details(self):
        self.add(name="Asha", email="asha@example.com", phone="+91 98765 43210")
        body = call(request("/v1/license/activate"))[1]
        self.assertEqual(("Asha", "asha@example.com", "+91 98765 43210", 365), (body["name"], body["email"], body["phone"], body["durationDays"]))
        self.assertGreater(body["activatedAt"], 0)

    def test_answer_has_empty_details_when_none_were_entered(self):
        self.add()
        body = call(request("/v1/license/activate"))[1]
        self.assertEqual(("", "", ""), (body["name"], body["email"], body["phone"]))

    def test_first_activation_binds_and_starts_the_clock(self):
        self.add()
        before = int(time.time())
        code, body = call(request("/v1/license/activate"))
        self.assertEqual(200, code)
        self.assertTrue(body["active"])
        self.assertGreaterEqual(body["expiresAt"], before + 365 * 86_400)
        self.assertEqual("device-1", self.table.items[lic.key_hash(KEY)]["uid"])
        self.assertTrue(call(request("/v1/license/status"))[1]["active"])

    def test_lifetime_key_never_expires(self):
        self.add(durationDays=0)
        body = call(request("/v1/license/activate"))[1]
        self.assertTrue(body["active"])
        self.assertEqual(0, body["expiresAt"])

    def test_store_set_expiry_is_kept(self):
        self.add(expiresAt=int(time.time()) + 1000)
        self.assertEqual(self.table.items[lic.key_hash(KEY)]["expiresAt"], call(request("/v1/license/activate"))[1]["expiresAt"])

    def test_expired_and_revoked_keys_are_refused(self):
        self.add(expiresAt=int(time.time()) - 1)
        self.assertEqual("expired", call(request("/v1/license/activate"))[1]["reason"])
        self.add(status="revoked")
        self.assertEqual("revoked", call(request("/v1/license/status"))[1]["reason"])

    def test_status_does_not_bind_a_key(self):
        self.add()
        self.assertEqual("moved", call(request("/v1/license/status"))[1]["reason"])
        self.assertNotIn("uid", self.table.items[lic.key_hash(KEY)])

    def test_key_moves_to_a_new_device_and_the_old_one_locks(self):
        self.add(uid="old-phone", activatedAt=1, expiresAt=int(time.time()) + 1000)
        self.assertTrue(call(request("/v1/license/activate", uid="new-phone"))[1]["active"])
        self.assertEqual("moved", call(request("/v1/license/status", uid="old-phone"))[1]["reason"])
        # Moving back within a day is refused, so one key can't be passed between phones.
        self.assertEqual("moved_recently", call(request("/v1/license/activate", uid="old-phone"))[1]["reason"])
        self.assertTrue(call(request("/v1/license/status", uid="new-phone"))[1]["active"])

    def test_moving_does_not_restart_the_clock(self):
        expires = int(time.time()) + 1000
        self.add(uid="old-phone", activatedAt=1, expiresAt=expires)
        self.assertEqual(expires, call(request("/v1/license/activate", uid="new-phone"))[1]["expiresAt"])

    def test_activating_again_on_the_same_device_is_a_no_op(self):
        self.add(uid="device-1", activatedAt=1, movedAt=int(time.time()), expiresAt=int(time.time()) + 1000)
        self.assertTrue(call(request("/v1/license/activate"))[1]["active"])

    def test_race_with_another_device_is_refused(self):
        self.add()
        original = self.table.get_item

        def stale_read(Key, **kw):
            found = original(Key)
            self.table.items[Key["keyHash"]]["uid"] = "sneaky-phone"
            return found

        self.table.get_item = stale_read
        self.assertEqual("moved_recently", call(request("/v1/license/activate"))[1]["reason"])
        self.assertEqual("sneaky-phone", self.table.items[lic.key_hash(KEY)]["uid"])

    def test_revoke_during_activation_is_not_answered_active(self):
        self.add()
        original = self.table.get_item
        calls = []

        def revoked_after_first_read(Key, **kw):
            found = original(Key)
            if not calls:
                self.table.items[Key["keyHash"]]["status"] = "revoked"
            calls.append(1)
            return found

        self.table.get_item = revoked_after_first_read
        body = call(request("/v1/license/activate"))[1]
        self.assertEqual((False, "revoked"), (body["active"], body["reason"]))
        self.assertNotIn("uid", self.table.items[lic.key_hash(KEY)])

    def test_expiry_during_activation_is_not_answered_active(self):
        self.add()
        original = self.table.get_item
        calls = []

        def expired_after_first_read(Key, **kw):
            found = original(Key)
            if not calls:
                self.table.items[Key["keyHash"]]["expiresAt"] = int(time.time()) - 5
            calls.append(1)
            return found

        self.table.get_item = expired_after_first_read
        body = call(request("/v1/license/activate"))[1]
        self.assertEqual((False, "expired"), (body["active"], body["reason"]))


if __name__ == "__main__":
    unittest.main()
