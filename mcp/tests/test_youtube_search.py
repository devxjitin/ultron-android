"""Offline contract/security tests for the read-only AWS YouTube MCP."""
import base64
import importlib.util
import json
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "web-extract"))
spec = importlib.util.spec_from_file_location("youtube_search_lambda", ROOT / "youtube-search" / "lambda_function.py")
yt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(yt)


def rpc(method, params=None):
    return {"jsonrpc": "2.0", "id": 1, "method": method, "params": params or {}}


def event(body=None):
    return {"rawPath": "/v1/youtube/mcp", "requestContext": {"http": {"method": "POST"},
            "authorizer": {"jwt": {"claims": {"sub": "test-device"}}}}, "body": json.dumps(body or rpc("ping"))}


class YouTubeSearchTests(unittest.TestCase):
    def test_discovery_has_only_four_read_tools(self):
        tools = yt._rpc(rpc("tools/list"))["result"]["tools"]
        self.assertEqual({tool["name"] for tool in tools}, {
            "youtube_search", "youtube_video_info", "youtube_transcript", "youtube_list_videos"})
        self.assertTrue(all(tool["annotations"]["readOnlyHint"] for tool in tools))
        self.assertNotIn("download_youtube_video", str(tools))
        self.assertEqual("youtube-search-aws", yt._rpc(rpc("initialize"))["result"]["serverInfo"]["name"])

    def test_search_is_bounded_and_marks_untrusted_results(self):
        with patch.object(yt, "_extract", return_value={"entries": [{"id": "BaW_jenozKc", "title": "Example", "url": "https://cdn.invalid/media.mp4", "formats": [{"url": "secret"}]}]}) as extract:
            result = yt.search({"query": "music", "limit": 3})
        extract.assert_called_once_with("ytsearch3:music", 3, flat=True)
        self.assertIn("untrusted", result)
        self.assertIn("https://www.youtube.com/watch?v=BaW_jenozKc", result)
        self.assertNotIn("media.mp4", result)
        self.assertNotIn("secret", result)

    def test_video_and_channel_addresses_are_normalized(self):
        for address in ("BaW_jenozKc", "https://youtu.be/BaW_jenozKc", "https://www.youtube.com/watch?v=BaW_jenozKc&list=PLwhatever", "https://youtube.com/shorts/BaW_jenozKc"):
            self.assertEqual("https://www.youtube.com/watch?v=BaW_jenozKc", yt.video_url(address))
        self.assertEqual("https://www.youtube.com/@example/videos", yt.source_url("@example"))
        self.assertEqual("https://www.youtube.com/@example/videos", yt.source_url("https://www.youtube.com/@example/videos"))

    def test_external_addresses_and_extractor_pseudo_urls_are_rejected(self):
        for address in ("http://127.0.0.1/", "https://youtube.com.evil.test/watch?v=BaW_jenozKc", "https://youtube.com@evil.test/watch?v=BaW_jenozKc", "file:///etc/passwd", "ytsearch1000:hello", "https://youtube.com:444/watch?v=BaW_jenozKc", "https://youtube.com/redirect?q=https://example.com"):
            with self.subTest(address=address), self.assertRaises(ValueError):
                yt.video_url(address)
            with self.subTest(source=address), self.assertRaises(ValueError):
                yt.source_url(address)

    def test_bad_arguments_never_extract(self):
        cases = [("youtube_search", {"query": ""}), ("youtube_search", {"query": "ok", "limit": 26}),
                 ("youtube_search", {"query": "ok", "limit": True}), ("youtube_video_info", {"video": 123}),
                 ("youtube_list_videos", {"source": "@test", "limit": "5"}),
                 ("youtube_transcript", {"video": "BaW_jenozKc", "language": "../../"}),
                 ("youtube_search", {"query": "ok", "download": True})]
        with patch.object(yt, "_extract") as extract:
            for name, args in cases:
                with self.subTest(name=name, args=args):
                    result = yt._rpc(rpc("tools/call", {"name": name, "arguments": args}))["result"]
                    self.assertTrue(result["isError"])
            extract.assert_not_called()

    def test_protocol_and_download_rejection(self):
        self.assertIsNone(yt._rpc({"jsonrpc": "2.0", "method": "notifications/initialized"}))
        for message in (None, 1, [], {}, {"method": "tools/list"}):
            self.assertEqual(-32600, yt._rpc(message)["error"]["code"])
        self.assertEqual(-32602, yt._rpc(rpc("tools/call", {"name": "download_youtube_video", "arguments": {}}))["error"]["code"])
        self.assertEqual(-32602, yt._rpc(rpc("tools/call", []))["error"]["code"])
        self.assertEqual(-32602, yt._rpc(rpc("tools/call", {"name": "youtube_search", "arguments": []}))["error"]["code"])

    def test_upstream_failure_and_timeout_are_tool_errors(self):
        for failure in (yt.LookupFailure("YouTube blocked this AWS request."), TimeoutError(), RuntimeError("secret-url")):
            with patch.object(yt, "_extract", side_effect=failure):
                result = yt._rpc(rpc("tools/call", {"name": "youtube_search", "arguments": {"query": "test"}}))["result"]
                self.assertTrue(result["isError"])
                self.assertNotIn("secret-url", str(result))

    def test_transcript_prefers_manual_and_does_not_fetch_media(self):
        info = {"subtitles": {"en": [{"ext": "json3", "url": "https://www.youtube.com/api/timedtext?x=manual"}]},
                "automatic_captions": {"en": [{"ext": "json3", "url": "automatic"}]}}
        with patch.object(yt, "_extract", return_value=info), patch.object(yt, "_captions", return_value=["[0s] Hello"]) as captions:
            result = yt.transcript({"video": "BaW_jenozKc"})
        self.assertIn('"automatic": false', result)
        captions.assert_called_once_with("https://www.youtube.com/api/timedtext?x=manual")

    def test_caption_fetch_rejects_arbitrary_hosts(self):
        for address in ("https://127.0.0.1/caption", "https://youtube.com.evil.test/caption", "http://www.youtube.com/api/timedtext", "https://user@www.youtube.com/api/timedtext"):
            with self.subTest(address=address), self.assertRaises(yt.LookupFailure):
                yt._captions(address)

    def test_extract_never_downloads_and_limits_lazy_entries(self):
        created = []
        class FakeYdl:
            def __init__(self, options):
                created.append(options)
            def __enter__(self):
                return self
            def __exit__(self, *args):
                return False
            def extract_info(self, address, download):
                self_test.assertFalse(download)
                return {"entries": ({"id": str(i)} for i in range(100))}
        self_test = self
        module = types.SimpleNamespace(YoutubeDL=FakeYdl)
        utils = types.SimpleNamespace(DownloadError=type("DownloadError", (Exception,), {}))
        with patch.dict(sys.modules, {"yt_dlp": module, "yt_dlp.utils": utils}):
            result = yt._extract("ytsearch2:test", 2, flat=True)
        self.assertEqual(2, len(result["entries"]))
        self.assertTrue(created[0]["skip_download"])
        self.assertFalse(created[0]["cachedir"])

    def test_route_and_identity_are_checked_before_appcheck(self):
        for field in ("path", "identity"):
            request = event()
            if field == "path":
                request["rawPath"] = "/wrong"
            else:
                request["requestContext"].pop("authorizer")
            with patch.object(yt.appcheck, "check") as check:
                self.assertIn(yt.lambda_handler(request, None)["statusCode"], (401, 404))
                check.assert_not_called()

    def test_appcheck_enforced(self):
        with patch.object(yt.appcheck, "check", return_value={"statusCode": 401}) as check:
            self.assertEqual(401, yt.lambda_handler(event(), None)["statusCode"])
            check.assert_called_once()

    def test_http_bodies_and_notifications(self):
        with patch.object(yt.appcheck, "check", return_value=None):
            self.assertEqual(200, yt.lambda_handler(event(), None)["statusCode"])
            request = event()
            request["isBase64Encoded"] = True
            request["body"] = base64.b64encode(json.dumps(rpc("ping")).encode()).decode()
            self.assertEqual(200, yt.lambda_handler(request, None)["statusCode"])
            request["body"] = "not base64!"
            self.assertEqual(400, yt.lambda_handler(request, None)["statusCode"])
            request = event()
            request["body"] = "x" * (yt.MAX_BODY + 1)
            self.assertEqual(413, yt.lambda_handler(request, None)["statusCode"])
            self.assertEqual(202, yt.lambda_handler(event({"jsonrpc": "2.0", "method": "notifications/initialized"}), None)["statusCode"])


if __name__ == "__main__":
    unittest.main()
