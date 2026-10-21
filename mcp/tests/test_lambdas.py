"""Offline tests for the MCP Lambda handlers. Outbound Google calls are replaced by a fake.

Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import base64
import email
import importlib.util
import json
import os
import pathlib
import time
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SECRET = "s3cret"
os.environ["ACCESS_TOKEN"] = SECRET


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


google = load("google_lambda", "google-workspace/lambda_function.py")
web = load("web_lambda", "web-extract/lambda_function.py")


def event(path, body, base64_encoded=False):
    raw = json.dumps(body)
    if base64_encoded:
        raw = base64.b64encode(raw.encode()).decode()
    return {"rawPath": path, "requestContext": {"http": {"method": "POST"}}, "body": raw,
            "isBase64Encoded": base64_encoded, "headers": {"authorization": "Bearer token"}}


class RpcEnvelopeTests(unittest.TestCase):
    """Both handlers must answer malformed JSON-RPC with an error instead of crashing."""

    targets = ((google, f"/{SECRET}/gmail"), (web, f"/{SECRET}"))

    def test_non_object_bodies_get_invalid_request(self):
        for module, path in self.targets:
            for body in (None, 42, "text"):
                with self.subTest(module=module.__name__, body=body):
                    response = module.lambda_handler(event(path, body), None)
                    self.assertEqual(200, response["statusCode"])
                    self.assertEqual(-32600, json.loads(response["body"])["error"]["code"])

    def test_a_bad_batch_member_does_not_fail_the_valid_ones(self):
        for module, path in self.targets:
            with self.subTest(module=module.__name__):
                response = module.lambda_handler(event(path, [None, {"jsonrpc": "2.0", "id": 1, "method": "ping"}]), None)
                answers = json.loads(response["body"])
                self.assertEqual(-32600, answers[0]["error"]["code"])
                self.assertEqual({}, answers[1]["result"])

    def test_wrong_params_and_arguments_types_are_invalid_params(self):
        for module, path in self.targets:
            for params in ([], {"name": "x", "arguments": 5}):
                with self.subTest(module=module.__name__, params=params):
                    body = {"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": params}
                    answer = json.loads(module.lambda_handler(event(path, body), None)["body"])
                    self.assertEqual(-32602, answer["error"]["code"])

    def test_base64_bodies_are_decoded(self):
        for module, path in self.targets:
            with self.subTest(module=module.__name__):
                body = {"jsonrpc": "2.0", "id": 1, "method": "ping"}
                answer = json.loads(module.lambda_handler(event(path, body, base64_encoded=True), None)["body"])
                self.assertEqual({}, answer["result"])

    def test_wrong_secret_path_is_not_found(self):
        response = google.lambda_handler(event("/wrong/gmail", {}), None)
        self.assertEqual(404, response["statusCode"])


class GoogleToolTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.original = google._google
        google._google = self.fake

    def tearDown(self):
        google._google = self.original
        google._deadline = None

    def fake(self, method, url, token, params=None, body=None, raw=False):
        self.calls.append((method, url, params, body))
        if "/threads/" in url:
            return {"messages": [
                {"payload": {"headers": [{"name": "Subject", "value": "Hello"}, {"name": "Message-ID", "value": "<a@x>"}]}},
                {"payload": {"headers": [{"name": "Message-ID", "value": "<b@x>"}, {"name": "References", "value": "<a@x>"}]}}]}
        if url.endswith("/drafts"):
            return {"id": "d1"}
        if url.endswith("/messages"):
            return {"messages": [{"id": "1"}, {"id": "2"}], "nextPageToken": "NEXT"}
        if "/messages/" in url:
            return {"id": url[-1], "payload": {"headers": []}}
        if url.endswith("/files"):
            return {"files": [{"id": "f"}], "nextPageToken": "MORE"}
        if "docs.googleapis" in url and method == "GET":
            return {"title": "T", "tabs": [
                {"tabProperties": {"tabId": "t1", "title": "One"},
                 "documentTab": {"body": {"content": [{"endIndex": 5, "paragraph": {"elements": [{"textRun": {"content": "first\n"}}]}}]}},
                 "childTabs": [{"tabProperties": {"tabId": "t2", "title": "Two"},
                                "documentTab": {"body": {"content": [{"endIndex": 9, "paragraph": {"elements": [{"textRun": {"content": "nested\n"}}]}}]}}}]}]}
        return {}

    def test_reply_draft_carries_threading_headers_and_subject(self):
        google.gm_draft({"to": "a@b.c", "subject": "ignored", "body": "hi", "thread_id": "th"}, "tok")
        raw = [c for c in self.calls if c[1].endswith("/drafts")][0][3]["message"]["raw"]
        message = email.message_from_bytes(base64.urlsafe_b64decode(raw))
        self.assertEqual("Re: Hello", message["Subject"])
        self.assertEqual("<b@x>", message["In-Reply-To"])
        self.assertEqual("<a@x> <b@x>", message["References"])

    def test_docs_read_includes_nested_tabs(self):
        text = google.docs_read({"document_id": "d"}, "tok")
        self.assertIn("first", text)
        self.assertIn("nested", text)
        self.assertIn("tab_id t2", text)

    def test_docs_append_targets_the_chosen_tab(self):
        google.docs_append({"document_id": "d", "text": "z", "tab_id": "t2"}, "tok")
        location = self.calls[-1][3]["requests"][0]["insertText"]["location"]
        self.assertEqual({"index": 8, "tabId": "t2"}, location)

    def test_unknown_tab_is_reported_not_written(self):
        result = google.docs_append({"document_id": "d", "text": "z", "tab_id": "nope"}, "tok")
        self.assertIn("not_found", result)
        self.assertFalse([c for c in self.calls if c[0] == "POST"])

    def test_listings_return_the_next_page_token(self):
        self.assertEqual("NEXT", json.loads(google.gm_search({"query": "a"}, "t"))["next_page_token"])
        self.assertEqual("MORE", json.loads(google.dr_search({"query": "a"}, "t"))["next_page_token"])

    def test_long_text_is_read_in_windows_with_a_continuation_hint(self):
        first = google._window("x" * 50, 0, 20)
        self.assertIn("offset=20", first)
        last = google._window("x" * 50, 40, 20)
        self.assertNotIn("more text", last)

    def test_an_exhausted_deadline_stops_further_google_calls(self):
        google._deadline = time.monotonic() - 1
        with self.assertRaises(RuntimeError):
            google._call_timeout()


if __name__ == "__main__":
    unittest.main()
