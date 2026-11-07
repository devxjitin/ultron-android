"""Offline tests for the feature-requests Lambda. DynamoDB and SES are replaced by fakes.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import importlib.util
import json
import os
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
os.environ.update({"TABLE": "requests", "DEVELOPER_EMAIL": "dev@example.com", "SENDER_EMAIL": "noreply@example.com"})

spec = importlib.util.spec_from_file_location("requests_lambda", ROOT / "feature-requests/lambda_function.py")
fr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fr)


class FakeTable:
    def __init__(self):
        self.items = {}
        self.counts = {}

    def update_item(self, Key, **_):
        self.counts[Key["id"]] = self.counts.get(Key["id"], 0) + 1
        return {"Attributes": {"count": self.counts[Key["id"]]}}

    def put_item(self, Item):
        self.items[Item["id"]] = Item


class FakeMail:
    def __init__(self, fail=False):
        self.sent = []
        self.fail = fail

    def send_email(self, **params):
        if self.fail:
            raise RuntimeError("not verified")
        self.sent.append(params)


def event(body, uid="user-1", path="/v1/requests", method="POST"):
    return {
        "rawPath": path,
        "requestContext": {"http": {"method": method}, "authorizer": {"jwt": {"claims": {"sub": uid}}}},
        "body": json.dumps(body),
    }


GOOD = {"type": "connector", "name": "Spotify", "details": "Play songs and playlists by voice.", "examples": "play my gym playlist"}


class FeatureRequestTest(unittest.TestCase):
    def setUp(self):
        fr._table = FakeTable()
        fr._ses = FakeMail()

    def call(self, body, **kw):
        response = fr.lambda_handler(event(body, **kw), None)
        return response["statusCode"], json.loads(response["body"])

    def test_a_good_request_is_stored_and_emailed(self):
        status, body = self.call({**GOOD, "email": "me@example.com", "appVersion": "1.9.0"})
        self.assertEqual(200, status)
        self.assertTrue(body["emailed"])
        stored = fr._table.items[body["id"]]
        self.assertEqual("Spotify", stored["name"])
        self.assertEqual("user-1", stored["uid"])
        mail = fr._ses.sent[0]
        self.assertEqual(["dev@example.com"], mail["Destination"]["ToAddresses"])
        self.assertEqual(["me@example.com"], mail["ReplyToAddresses"])
        self.assertIn("Connector request: Spotify", mail["Message"]["Subject"]["Data"])
        self.assertIn("play my gym playlist", mail["Message"]["Body"]["Text"]["Data"])

    def test_a_failed_email_still_keeps_the_request(self):
        fr._ses = FakeMail(fail=True)
        status, body = self.call(GOOD)
        self.assertEqual(200, status)
        self.assertFalse(body["emailed"])
        self.assertIn(body["id"], fr._table.items)

    def test_bad_forms_are_refused(self):
        self.assertEqual((400, {"error": "bad_type"}), self.call({**GOOD, "type": "app"}))
        self.assertEqual((400, {"error": "bad_name"}), self.call({**GOOD, "name": " a "}))
        self.assertEqual((400, {"error": "bad_details"}), self.call({**GOOD, "details": "short"}))
        self.assertEqual((400, {"error": "bad_email"}), self.call({**GOOD, "email": "nope"}))
        self.assertEqual({}, fr._table.items)

    def test_subject_cannot_be_split_into_header_lines(self):
        self.call({**GOOD, "name": "Evil\r\nBcc: x@example.com"})
        self.assertNotIn("\n", fr._ses.sent[0]["Message"]["Subject"]["Data"])
        self.assertNotIn("\r", fr._ses.sent[0]["Message"]["Subject"]["Data"])

    def test_one_user_can_send_only_a_few_a_day(self):
        for _ in range(fr.DAILY_LIMIT):
            self.assertEqual(200, self.call(GOOD)[0])
        self.assertEqual((429, {"error": "too_many"}), self.call(GOOD))
        self.assertEqual(200, self.call(GOOD, uid="user-2")[0])

    def test_unknown_routes_and_anonymous_callers_are_refused(self):
        self.assertEqual(404, fr.lambda_handler(event(GOOD, path="/v1/other"), None)["statusCode"])
        self.assertEqual(401, fr.lambda_handler(event(GOOD, uid=""), None)["statusCode"])


if __name__ == "__main__":
    unittest.main()
