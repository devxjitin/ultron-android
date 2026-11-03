"""Offline tests for the Ultronapp admin Lambda. DynamoDB is replaced by a fake.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import importlib.util
import json
import os
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.environ.update({"TABLE": "licenses", "ADMIN_EMAILS": "boss@example.com, Second@Example.com"})

spec = importlib.util.spec_from_file_location("license_admin_lambda", ROOT / "license-admin/lambda_function.py")
adm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(adm)

# Keys made here must be readable by the app Lambda: same format check, same hash.
lic_spec = importlib.util.spec_from_file_location("license_lambda_for_admin", ROOT / "license/lambda_function.py")
lic = importlib.util.module_from_spec(lic_spec)
lic_spec.loader.exec_module(lic)


class ConditionalCheckFailedException(Exception):
    pass


class FakeTable:
    """Understands only what the admin Lambda sends: scan, put_item and SET #a = :a, ... updates."""

    def __init__(self):
        self.items = {}

    def scan(self, **kwargs):
        return {"Items": [dict(i) for i in self.items.values()]}

    def put_item(self, Item, ConditionExpression):
        if Item["keyHash"] in self.items:
            raise ConditionalCheckFailedException("ConditionalCheckFailed")
        self.items[Item["keyHash"]] = dict(Item)

    def update_item(self, Key, UpdateExpression, ConditionExpression, ExpressionAttributeNames,
                    ExpressionAttributeValues, ReturnValues):
        item = self.items.get(Key["keyHash"])
        if item is None:
            raise ConditionalCheckFailedException("ConditionalCheckFailed")
        for part in UpdateExpression.removeprefix("SET ").split(", "):
            field, value = part.split(" = ")
            item[ExpressionAttributeNames[field]] = ExpressionAttributeValues[value]
        return {"Attributes": dict(item)}


def request(method, path, body=None, email="boss@example.com", verified="true"):
    claims = {"sub": "uid-1"}
    if email:
        claims.update({"email": email, "email_verified": verified})
    return {
        "rawPath": path,
        "requestContext": {"http": {"method": method}, "authorizer": {"jwt": {"claims": claims}}},
        "body": json.dumps(body) if body is not None else "",
        "isBase64Encoded": False,
    }


def call(event):
    response = adm.lambda_handler(event, None)
    return response["statusCode"], json.loads(response["body"])


class AdminTests(unittest.TestCase):
    def setUp(self):
        self.table = FakeTable()
        adm._table = self.table

    def create(self, **body):
        code, answer = call(request("POST", "/v1/admin/licenses", body))
        self.assertEqual(200, code, answer)
        return answer

    def test_only_verified_admin_emails_get_in(self):
        for event in (
            request("GET", "/v1/admin/licenses", email=""),
            request("GET", "/v1/admin/licenses", email="stranger@example.com"),
            request("GET", "/v1/admin/licenses", verified="false"),
        ):
            self.assertEqual((403, {"error": "forbidden"}), call(event))
        self.assertEqual(200, call(request("GET", "/v1/admin/licenses"))[0])
        self.assertEqual(200, call(request("GET", "/v1/admin/licenses", email="SECOND@example.com"))[0])

    def test_empty_admin_list_locks_everyone_out(self):
        os.environ["ADMIN_EMAILS"] = ""
        try:
            self.assertEqual(403, call(request("GET", "/v1/admin/licenses"))[0])
        finally:
            os.environ["ADMIN_EMAILS"] = "boss@example.com, Second@Example.com"

    def test_create_stores_only_the_hash_and_returns_the_key_once(self):
        answer = self.create(plan="monthly", durationDays=30, name=" Asha ", email="asha@example.com", phone="+91 98765 43210")
        key = answer["keys"][0]
        self.assertEqual(key, lic.normalize(key))
        stored = self.table.items[lic.key_hash(key)]
        self.assertNotIn(key, json.dumps(stored))
        self.assertEqual(("monthly", 30, "Asha", key[-4:]), (stored["plan"], stored["durationDays"], stored["name"], stored["hint"]))
        listed = call(request("GET", "/v1/admin/licenses"))[1]["licenses"]
        self.assertEqual(1, len(listed))
        self.assertEqual("unused", listed[0]["state"])
        self.assertNotIn(key, json.dumps(listed))

    def test_create_defaults_and_count(self):
        answer = self.create(count=3)
        self.assertEqual(3, len(set(answer["keys"])))
        first = answer["licenses"][0]
        self.assertEqual(("yearly", 365), (first["plan"], first["durationDays"]))

    def test_create_validates_input(self):
        for body, error in (
            ({"email": "nope"}, "bad_email"),
            ({"phone": "abc"}, "bad_phone"),
            ({"name": "x" * 81}, "name_too_long"),
            ({"plan": " "}, "plan_required"),
            ({"durationDays": -1}, "bad_durationDays"),
            ({"durationDays": "30"}, "bad_durationDays"),
            ({"count": 0}, "bad_count"),
            ({"count": 51}, "bad_count"),
        ):
            self.assertEqual((400, {"error": error}), call(request("POST", "/v1/admin/licenses", body)), body)
        self.assertEqual(400, call(request("POST", "/v1/admin/licenses", None) | {"body": "not json"})[0])
        self.assertEqual({}, self.table.items)

    def test_update_edits_details_and_revokes(self):
        listed = self.create(name="Asha")["licenses"][0]
        path = f"/v1/admin/licenses/{listed['id']}"
        code, body = call(request("PATCH", path, {"phone": "9876543210", "status": "revoked", "plan": "gift"}))
        self.assertEqual(200, code)
        self.assertEqual(("9876543210", "revoked", "gift", "Asha"),
                         (body["license"]["phone"], body["license"]["state"], body["license"]["plan"], body["license"]["name"]))
        self.assertEqual("active", call(request("PATCH", path, {"status": "active"}))[1]["license"]["status"])

    def test_update_can_extend_expiry_and_rejects_bad_values(self):
        listed = self.create()["licenses"][0]
        path = f"/v1/admin/licenses/{listed['id']}"
        self.assertEqual(1_900_000_000, call(request("PATCH", path, {"expiresAt": 1_900_000_000}))[1]["license"]["expiresAt"])
        self.assertEqual((400, {"error": "bad_status"}), call(request("PATCH", path, {"status": "banned"})))
        self.assertEqual((400, {"error": "nothing_to_change"}), call(request("PATCH", path, {"colour": "red"})))

    def test_update_unknown_id_is_not_found(self):
        self.assertEqual(404, call(request("PATCH", "/v1/admin/licenses/" + "0" * 64, {"name": "x"}))[0])
        self.assertEqual(404, call(request("PATCH", "/v1/admin/licenses/not-a-hash", {"name": "x"}))[0])

    def test_allow_move_clears_the_cooldown(self):
        listed = self.create()["licenses"][0]
        self.table.items[listed["id"]].update({"uid": "phone-1", "movedAt": 999})
        code, body = call(request("POST", f"/v1/admin/licenses/{listed['id']}/allow-move"))
        self.assertEqual((200, 0), (code, body["license"]["movedAt"]))
        self.assertEqual("phone-1", self.table.items[listed["id"]]["uid"])

    def test_state_and_newest_first(self):
        import time
        now = int(time.time())
        self.table.items = {
            "a": {"keyHash": "a", "status": "active", "createdAt": 1, "uid": "u", "expiresAt": now + 100},
            "b": {"keyHash": "b", "status": "active", "createdAt": 3, "uid": "u", "expiresAt": now - 1},
            "c": {"keyHash": "c", "status": "revoked", "createdAt": 2},
        }
        listed = call(request("GET", "/v1/admin/licenses"))[1]["licenses"]
        self.assertEqual(["b", "c", "a"], [l["id"] for l in listed])
        self.assertEqual(["expired", "revoked", "active"], [l["state"] for l in listed])

    def test_unknown_route(self):
        self.assertEqual(404, call(request("DELETE", "/v1/admin/licenses"))[0])
        self.assertEqual(404, call(request("GET", "/v1/admin/other"))[0])


if __name__ == "__main__":
    unittest.main()
