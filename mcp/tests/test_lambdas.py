"""Offline tests for the MCP Lambda handlers.
Run from the repository root:  python -m unittest discover -s mcp/tests -v
"""
import base64
import importlib.util
import json
import os
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
# On Lambda, appcheck.py sits next to lambda_function.py; the tests load the handler by path, so add that folder.
sys.path.insert(0, str(ROOT / "web-extract"))
SECRET = "s3cret"
os.environ["ACCESS_TOKEN"] = SECRET


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


web = load("web_lambda", "web-extract/lambda_function.py")


def event(path, body, base64_encoded=False):
    raw = json.dumps(body)
    if base64_encoded:
        raw = base64.b64encode(raw.encode()).decode()
    return {"rawPath": path, "requestContext": {"http": {"method": "POST"}}, "body": raw,
            "isBase64Encoded": base64_encoded, "headers": {"authorization": "Bearer token"}}


class RpcEnvelopeTests(unittest.TestCase):
    """The handler must answer malformed JSON-RPC with an error instead of crashing."""

    targets = ((web, f"/{SECRET}"),)

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
        response = web.lambda_handler(event("/wrong", {}), None)
        self.assertEqual(404, response["statusCode"])


class JwtModeTests(unittest.TestCase):
    """Behind API Gateway the route decides, not a secret path segment."""

    def setUp(self):
        os.environ["JWT_AUTH"] = "1"
        self.addCleanup(os.environ.pop, "JWT_AUTH", None)

    def test_the_web_route_runs_without_a_secret_segment(self):
        response = web.lambda_handler(event("/v1/web/mcp", {"jsonrpc": "2.0", "id": 1, "method": "ping"}), None)
        self.assertEqual(200, response["statusCode"])

    def test_other_paths_are_not_found_even_with_the_old_secret(self):
        self.assertEqual(404, web.lambda_handler(event(f"/{SECRET}", {}), None)["statusCode"])
        self.assertEqual(404, web.lambda_handler(event("/v1/other", {}), None)["statusCode"])


if __name__ == "__main__":
    unittest.main()
