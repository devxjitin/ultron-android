"""Offline contract/security tests for the read-only AWS web search MCP."""
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "web-extract"))
spec = importlib.util.spec_from_file_location("web_search_lambda", ROOT / "web-search" / "lambda_function.py")
ws = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ws)


def rpc(method, params=None):
    return {"jsonrpc": "2.0", "id": 1, "method": method, "params": params or {}}


def event(body=None):
    return {"rawPath": "/v1/search/mcp", "requestContext": {"http": {"method": "POST"},
            "authorizer": {"jwt": {"claims": {"sub": "test-device"}}}}, "body": json.dumps(body or rpc("ping"))}


def call(args):
    return ws._rpc(rpc("tools/call", {"name": "web_search", "arguments": args}))["result"]


class WebSearchTests(unittest.TestCase):
    def test_discovery_has_one_read_only_tool(self):
        tools = ws._rpc(rpc("tools/list"))["result"]["tools"]
        self.assertEqual(["web_search"], [tool["name"] for tool in tools])
        self.assertTrue(tools[0]["annotations"]["readOnlyHint"])
        self.assertEqual("web-search-aws", ws._rpc(rpc("initialize"))["result"]["serverInfo"]["name"])

    def test_results_are_trimmed_and_marked_untrusted(self):
        rows = [{"title": "A", "href": "https://example.com/a", "body": "x" * 900},
                {"title": "Bad", "href": "javascript:alert(1)", "body": "no"},
                {"title": "B", "href": "http://example.org/b", "body": "ok"}]
        with patch.object(ws, "_ddgs_text", return_value=rows) as ddgs:
            result = call({"query": "news", "time": "week", "limit": 5})
        ddgs.assert_called_once_with("news", "w", 5)
        self.assertFalse(result["isError"])
        text = result["content"][0]["text"]
        self.assertTrue(text.startswith("status=ok\n"))
        self.assertIn("untrusted", text)
        hits = json.loads(text.split("\n", 2)[2])
        self.assertEqual(["https://example.com/a", "http://example.org/b"], [hit["url"] for hit in hits])
        self.assertEqual(500, len(hits[0]["snippet"]))
        self.assertNotIn("javascript", text)

    def test_empty_results_are_a_tool_error(self):
        with patch.object(ws, "_ddgs_text", return_value=[]):
            result = call({"query": "nothing"})
        self.assertTrue(result["isError"])
        self.assertIn("status=failed", result["content"][0]["text"])

    def test_bad_arguments_never_search(self):
        cases = [{"query": ""}, {"query": 5}, {"query": "ok", "limit": 11}, {"query": "ok", "limit": True},
                 {"query": "ok", "time": "decade"}, {"query": "ok", "extra": 1}, {"query": "x" * 501}]
        with patch.object(ws, "_ddgs_text") as ddgs:
            for args in cases:
                with self.subTest(args=args):
                    self.assertTrue(call(args)["isError"])
        ddgs.assert_not_called()

    def test_unexpected_failures_do_not_leak_details(self):
        with patch.object(ws, "_ddgs_text", side_effect=RuntimeError("secret proxy url")):
            result = call({"query": "ok"})
        self.assertTrue(result["isError"])
        self.assertNotIn("secret", result["content"][0]["text"])

    def test_handler_requires_sign_in_post_and_app_check(self):
        unsigned = event()
        unsigned["requestContext"]["authorizer"] = {}
        self.assertEqual(401, ws.lambda_handler(unsigned, None)["statusCode"])
        wrong = event()
        wrong["requestContext"]["http"]["method"] = "GET"
        self.assertEqual(405, ws.lambda_handler(wrong, None)["statusCode"])
        wrong_path = event()
        wrong_path["rawPath"] = "/v1/youtube/mcp"
        self.assertEqual(404, ws.lambda_handler(wrong_path, None)["statusCode"])
        with patch.object(ws.appcheck, "check", return_value={"statusCode": 401, "body": ""}):
            self.assertEqual(401, ws.lambda_handler(event(), None)["statusCode"])
        with patch.object(ws.appcheck, "check", return_value=None):
            self.assertEqual(200, ws.lambda_handler(event(), None)["statusCode"])

    def test_batches_and_large_bodies_are_refused(self):
        with patch.object(ws.appcheck, "check", return_value=None):
            batch = ws.lambda_handler(event([rpc("ping")]), None)
            self.assertEqual(-32600, json.loads(batch["body"])["error"]["code"])
            large = event()
            large["body"] = "x" * (ws.MAX_BODY + 1)
            self.assertEqual(413, ws.lambda_handler(large, None)["statusCode"])


if __name__ == "__main__":
    unittest.main()
