"""Offline tests for the Ultronapp admin Lambda. DynamoDB is replaced by a fake.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import importlib.util
import json
import os
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.environ.update({
    "TABLE": "licenses", "ADMIN_EMAILS": "boss@example.com, Second@Example.com",
    "SENDER_EMAIL": "no-reply@example.com", "SUPPORT_EMAIL": "help@example.com", "DOWNLOAD_URL": "https://example.com/get",
})

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

    def get_item(self, Key):
        item = self.items.get(Key["keyHash"])
        return {"Item": dict(item)} if item else {}

    def put_item(self, Item, ConditionExpression):
        if Item["keyHash"] in self.items:
            raise ConditionalCheckFailedException("ConditionalCheckFailed")
        self.items[Item["keyHash"]] = dict(Item)

    def update_item(self, Key, UpdateExpression, ExpressionAttributeNames, ExpressionAttributeValues,
                    ConditionExpression=None, ReturnValues=None):
        item = self.items.get(Key["keyHash"])
        if item is None:
            raise ConditionalCheckFailedException("ConditionalCheckFailed")
        for part in UpdateExpression.removeprefix("SET ").split(", "):
            field, value = part.split(" = ")
            item[ExpressionAttributeNames[field]] = ExpressionAttributeValues[value]
        return {"Attributes": dict(item)}


class FakeMail:
    def __init__(self, fail=False):
        self.sent = []
        self.fail = fail

    def send_email(self, **params):
        if self.fail:
            raise RuntimeError("not verified")
        self.sent.append(params)


# Everything a key must carry; tests change one field at a time.
BUYER = {"name": "Asha Verma", "email": "asha@example.com", "phone": "+91 98765 43210", "plan": "surge", "durationDays": 30}


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
        self.mail = FakeMail()
        adm._ses = self.mail

    def create(self, **body):
        code, answer = call(request("POST", "/v1/admin/licenses", {**BUYER, **body}))
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

    def test_create_makes_several_keys_at_once(self):
        answer = self.create(count=3)
        self.assertEqual(3, len(set(answer["keys"])))
        self.assertEqual(("surge", 30), (answer["licenses"][0]["plan"], answer["licenses"][0]["durationDays"]))

    def test_create_requires_the_buyer_details_plan_and_validity(self):
        for field in ("name", "email", "phone", "plan", "durationDays"):
            body = {k: v for k, v in BUYER.items() if k != field}
            self.assertEqual((400, {"error": f"{field}_required"}), call(request("POST", "/v1/admin/licenses", body)), field)
        for field in ("name", "email", "phone"):
            self.assertEqual((400, {"error": f"{field}_required"}),
                             call(request("POST", "/v1/admin/licenses", {**BUYER, field: "   "})), field)
        self.assertEqual({}, self.table.items)
        self.assertEqual([], self.mail.sent)

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
            self.assertEqual((400, {"error": error}), call(request("POST", "/v1/admin/licenses", {**BUYER, **body})), body)
        self.assertEqual(400, call(request("POST", "/v1/admin/licenses", None) | {"body": "not json"})[0])
        self.assertEqual({}, self.table.items)

    def test_the_key_is_emailed_to_the_buyer(self):
        answer = self.create()
        self.assertTrue(answer["emailed"])
        mail = self.mail.sent[0]
        key = answer["keys"][0]
        self.assertEqual(["asha@example.com"], mail["Destination"]["ToAddresses"])
        self.assertEqual("Ultron <no-reply@example.com>", mail["Source"])
        self.assertEqual(["help@example.com"], mail["ReplyToAddresses"])
        self.assertEqual("Your Ultron activation key", mail["Message"]["Subject"]["Data"])
        text, page = mail["Message"]["Body"]["Text"]["Data"], mail["Message"]["Body"]["Html"]["Data"]
        for body in (text, page):
            self.assertIn(key, body)
            self.assertIn("Asha Verma", body)
            self.assertIn("https://example.com/get", body)
            self.assertIn("30 days", body)
            self.assertIn("Surge", body)
            self.assertIn("Install anyway", body)
        self.assertGreater(call(request("GET", "/v1/admin/licenses"))[1]["licenses"][0]["emailedAt"], 0)

    def test_several_keys_go_in_one_email(self):
        answer = self.create(count=2)
        self.assertEqual(1, len(self.mail.sent))
        self.assertEqual("Your Ultron activation keys", self.mail.sent[0]["Message"]["Subject"]["Data"])
        for key in answer["keys"]:
            self.assertIn(key, self.mail.sent[0]["Message"]["Body"]["Text"]["Data"])

    def test_buyer_details_cannot_inject_html_into_the_email(self):
        self.create(name="<script>alert(1)</script>")
        page = self.mail.sent[0]["Message"]["Body"]["Html"]["Data"]
        self.assertNotIn("<script>", page)
        self.assertIn("&lt;script&gt;", page)

    def test_each_plan_names_its_own_length_in_the_email(self):
        for plan, days in (("spark", 7), ("apex", 90)):
            self.create(plan=plan, durationDays=days)
            text = self.mail.sent[-1]["Message"]["Body"]["Text"]["Data"]
            self.assertIn(f"Plan: {plan.title()}", text)
            self.assertIn(f"{days} days", text)

    def test_a_key_without_an_end_date_says_it_never_expires(self):
        self.create(plan="lifetime", durationDays=0)
        self.assertIn("never expires", self.mail.sent[0]["Message"]["Body"]["Text"]["Data"])

    def test_the_amount_is_stored_listed_and_shown_in_the_email(self):
        answer = self.create(plan="apex", durationDays=90, amount=1199)
        self.assertEqual(1199, answer["licenses"][0]["amount"])
        self.assertEqual(1199, self.table.items[adm.key_hash(answer["keys"][0])]["amount"])
        text = self.mail.sent[0]["Message"]["Body"]["Text"]["Data"]
        self.assertIn("Amount paid: Rs. 1,199", text)
        self.assertIn("Rs. 1,199", self.mail.sent[0]["Message"]["Body"]["Html"]["Data"])
        self.assertEqual(1199, call(request("GET", "/v1/admin/licenses"))[1]["licenses"][0]["amount"])

    def test_several_keys_email_the_total_paid(self):
        self.create(count=3, amount=499)
        self.assertIn("Amount paid: Rs. 1,497", self.mail.sent[0]["Message"]["Body"]["Text"]["Data"])

    def test_without_an_amount_it_is_zero_and_the_email_has_no_price(self):
        answer = self.create()
        self.assertEqual(0, answer["licenses"][0]["amount"])
        self.assertNotIn("Amount paid", self.mail.sent[0]["Message"]["Body"]["Text"]["Data"])

    def test_a_bad_amount_is_refused(self):
        for amount in (-1, "499", 1_000_001, 4.5):
            self.assertEqual((400, {"error": "bad_amount"}),
                             call(request("POST", "/v1/admin/licenses", {**BUYER, "amount": amount})), amount)
        self.assertEqual({}, self.table.items)

    def test_the_amount_can_be_corrected_later(self):
        listed = self.create(amount=499)["licenses"][0]
        code, body = call(request("PATCH", f"/v1/admin/licenses/{listed['id']}", {"amount": 0}))
        self.assertEqual((200, 0), (code, body["license"]["amount"]))

    def test_a_reissued_key_carries_no_amount_so_revenue_is_not_counted_twice(self):
        old = self.create(amount=499)
        answer = call(request("POST", f"/v1/admin/licenses/{old['licenses'][0]['id']}/reissue"))[1]
        self.assertEqual(0, answer["licenses"][0]["amount"])
        self.assertNotIn("Amount paid", self.mail.sent[1]["Message"]["Body"]["Text"]["Data"])

    def test_email_can_be_skipped(self):
        answer = self.create(sendEmail=False)
        self.assertFalse(answer["emailed"])
        self.assertEqual([], self.mail.sent)
        self.assertEqual(0, answer["licenses"][0]["emailedAt"])

    def test_a_failed_email_still_returns_the_key(self):
        adm._ses = FakeMail(fail=True)
        answer = self.create()
        self.assertFalse(answer["emailed"])
        self.assertEqual(1, len(answer["keys"]))
        self.assertIn(adm.key_hash(answer["keys"][0]), self.table.items)
        self.assertEqual(0, answer["licenses"][0]["emailedAt"])

    def test_reissue_revokes_the_old_key_and_emails_a_new_one(self):
        old = self.create()
        old_id = old["licenses"][0]["id"]
        code, answer = call(request("POST", f"/v1/admin/licenses/{old_id}/reissue"))
        self.assertEqual(200, code, answer)
        self.assertNotEqual(old["keys"][0], answer["keys"][0])
        self.assertTrue(answer["emailed"])
        self.assertEqual("revoked", self.table.items[old_id]["status"])
        new = self.table.items[adm.key_hash(answer["keys"][0])]
        self.assertEqual(("Asha Verma", "asha@example.com", "surge", 30, old_id),
                         (new["name"], new["email"], new["plan"], new["durationDays"], new["reissuedFrom"]))
        self.assertEqual(2, len(self.mail.sent))
        self.assertIn(answer["keys"][0], self.mail.sent[1]["Message"]["Body"]["Text"]["Data"])

    def test_reissue_of_an_unknown_key_is_not_found(self):
        self.assertEqual(404, call(request("POST", "/v1/admin/licenses/" + "0" * 64 + "/reissue"))[0])

    def test_update_edits_details_and_revokes(self):
        listed = self.create(name="Asha")["licenses"][0]
        path = f"/v1/admin/licenses/{listed['id']}"
        code, body = call(request("PATCH", path, {"phone": "9876543210", "status": "revoked", "plan": "gift"}))
        self.assertEqual(200, code)
        self.assertEqual(("9876543210", "revoked", "gift", "Asha"),
                         (body["license"]["phone"], body["license"]["state"], body["license"]["plan"], body["license"]["name"]))
        self.assertEqual("active", call(request("PATCH", path, {"status": "active"}))[1]["license"]["status"])

    def test_update_cannot_empty_the_buyer_details(self):
        listed = self.create()["licenses"][0]
        path = f"/v1/admin/licenses/{listed['id']}"
        for field in ("name", "email", "phone"):
            self.assertEqual((400, {"error": f"{field}_required"}), call(request("PATCH", path, {field: " "})), field)

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
