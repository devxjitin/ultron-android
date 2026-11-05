"""Offline reproductions for the 2026-10-21 audit; no live services or credentials.

These checks assert current defects, not desired regression-test behavior.
Run: python docs/audit_reproductions.py
"""
import base64
import importlib.util
import json
import os
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


web = load("web_audit", "mcp/web-extract/lambda_function.py")


def event(body, path, encoded=False):
    raw = json.dumps(body)
    return {"rawPath": path, "requestContext": {"http": {"method": "POST"}},
            "headers": {}, "body": base64.b64encode(raw.encode()).decode() if encoded else raw,
            "isBase64Encoded": encoded}


with patch.dict(os.environ, {"ACCESS_TOKEN": "audit-only"}):
    for module, path in ((web, "/audit-only"),):
        for payload in (None, 42, [None]):
            try:
                module.lambda_handler(event(payload, path), None)
            except AttributeError:
                print(f"CONFIRMED {module.__name__}: valid JSON {payload!r} crashes handler")
            else:
                raise AssertionError("Malformed JSON-RPC behavior changed; update audit")
        response = module.lambda_handler(event({"id": 1, "method": "ping"}, path, True), None)
        assert response["statusCode"] == 400
        print(f"CONFIRMED {module.__name__}: base64 Function URL body rejected")

# Deterministic replay of the Kotlin display algorithm for an already-buffered reply.
for length in (500, 5000, 10000):
    pending, ticks = length, 0
    while pending:
        pending -= min(max(2, min(12, pending // 40)), pending)
        ticks += 1
    print(f"DISPLAY MODEL: {length} buffered characters require {ticks * 16 / 1000:.3f}s of deliberate delay")

print("Offline reproductions completed; Kotlin timing is an algorithm replay, not a device benchmark.")
