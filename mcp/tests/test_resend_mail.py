"""Offline tests for resend_mail.py, which turns SES-shaped mail arguments into a Resend request.
Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import io
import json
import pathlib
import sys
import unittest
import urllib.error
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "license-admin"))
import resend_mail  # noqa: E402

PARAMS = {
    "Source": "Ultron <no-reply@example.com>",
    "Destination": {"ToAddresses": ["buyer@example.com"]},
    "Message": {
        "Subject": {"Data": "Your Ultron activation key", "Charset": "UTF-8"},
        "Body": {"Text": {"Data": "plain", "Charset": "UTF-8"}, "Html": {"Data": "<b>rich</b>", "Charset": "UTF-8"}},
    },
    "ReplyToAddresses": ["help@example.com"],
}


class FakeResponse:
    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False

    def read(self):
        return b'{"id": "abc"}'


class PayloadTest(unittest.TestCase):
    def test_all_parts_are_carried(self):
        self.assertEqual(resend_mail.to_payload(PARAMS), {
            "from": "Ultron <no-reply@example.com>", "to": ["buyer@example.com"], "subject": "Your Ultron activation key",
            "text": "plain", "html": "<b>rich</b>", "reply_to": ["help@example.com"],
        })

    def test_html_and_reply_to_are_optional(self):
        params = {**PARAMS, "Message": {"Subject": PARAMS["Message"]["Subject"], "Body": {"Text": PARAMS["Message"]["Body"]["Text"]}}}
        del params["ReplyToAddresses"]
        payload = resend_mail.to_payload(params)
        self.assertNotIn("html", payload)
        self.assertNotIn("reply_to", payload)


class SendTest(unittest.TestCase):
    def test_sends_one_authorised_post(self):
        with mock.patch.object(resend_mail, "_key", return_value="re_test"), \
                mock.patch("urllib.request.urlopen", return_value=FakeResponse()) as opened:
            resend_mail.ResendMail().send_email(**PARAMS)
        request = opened.call_args.args[0]
        self.assertEqual(request.full_url, "https://api.resend.com/emails")
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Authorization"), "Bearer re_test")
        self.assertEqual(request.get_header("User-agent"), "ultron-api/1.0")
        self.assertEqual(json.loads(request.data)["to"], ["buyer@example.com"])

    def test_a_refusal_raises_without_leaking_the_key(self):
        refusal = urllib.error.HTTPError("u", 403, "Forbidden", {}, io.BytesIO(b'{"message":"domain not verified"}'))
        with mock.patch.object(resend_mail, "_key", return_value="re_secret"), \
                mock.patch("urllib.request.urlopen", side_effect=refusal):
            with self.assertRaises(RuntimeError) as caught:
                resend_mail.ResendMail().send_email(**PARAMS)
        self.assertIn("403", str(caught.exception))
        self.assertNotIn("re_secret", str(caught.exception))

    def test_the_copy_in_every_lambda_is_identical(self):
        first = (ROOT / "license-admin" / "resend_mail.py").read_bytes()
        self.assertEqual(first, (ROOT / "feature-requests" / "resend_mail.py").read_bytes())


if __name__ == "__main__":
    unittest.main()
