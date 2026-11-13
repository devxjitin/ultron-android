"""Phase 0 probe for the Gemini Decisions API (v1beta, gemini-decision-1).

Measures latency (text and screenshot), input tokens per screenshot at each
resolution, and where the free tier starts returning 429.

Usage (key is read from the environment, never from argv or files):
    set GEMINI_API_KEY=...            (PowerShell: $env:GEMINI_API_KEY = "...")
    python scripts/decisions_probe.py [--image phone_ss.png] [--runs 5] [--burst 20]

Prints a markdown table; paste it into docs/DECISIONS_API_PHASE0.md.
"""
import argparse
import base64
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request

URL = "https://generativelanguage.googleapis.com/v1beta/decisions"
MODEL = "gemini-decision-1"

ROUTING_QUESTIONS = {
    "needs_whatsapp": {"type": "noul", "instructions": "Does the request need WhatsApp?"},
    "needs_calendar": {"type": "noul", "instructions": "Does the request need the calendar?"},
    "needs_phone_control": {"type": "noul", "instructions": "Does the request need controlling another app on the phone?"},
    "main_connector": {
        "type": "choice",
        "instructions": "Which connector is the main one for this request?",
        "criteria": {
            "whatsapp": "Messaging or calling on WhatsApp",
            "calendar": "Events, reminders, schedule",
            "phone_control": "Tapping or typing in another app",
            "none": "Just conversation",
        },
    },
}

SAFETY_QUESTIONS = {
    "is_irreversible": {"type": "noul", "instructions": "Is the planned action irreversible?"},
    "sends_to_other_person": {"type": "noul", "instructions": "Does it send something to another person?"},
    "involves_money": {"type": "noul", "instructions": "Does it involve money?"},
    "deletes_data": {"type": "noul", "instructions": "Does it delete data?"},
}

SCREEN_QUESTIONS = {
    "step_succeeded": {"type": "noul", "instructions": "Does this screen show the step succeeded?"},
    "blocker": {
        "type": "choice",
        "instructions": "What is blocking progress, if anything?",
        "criteria": {"none": "Nothing", "permission": "Permission prompt", "login": "Login wall",
                     "error": "Error message", "ad": "Advertisement", "update": "Update prompt"},
    },
}


def call(key, body):
    req = urllib.request.Request(
        URL, data=json.dumps(body).encode(),
        headers={"x-goog-api-key": key, "Content-Type": "application/json"}, method="POST")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.loads(r.read())
            status = r.status
    except urllib.error.HTTPError as e:
        data, status = {"error": e.read().decode(errors="replace")[:300]}, e.code
    except (urllib.error.URLError, OSError) as e:
        data, status = {"error": str(e)[:300]}, 0
    return status, (time.perf_counter() - t0) * 1000, data


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", default="phone_ss.png")
    ap.add_argument("--runs", type=int, default=5)
    ap.add_argument("--burst", type=int, default=20)
    a = ap.parse_args()
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        sys.exit("Set GEMINI_API_KEY first (use a test key).")

    rows = []

    def bench(name, body):
        ms, tokens = [], None
        for _ in range(a.runs):
            st, t, d = call(key, body)
            if st != 200:
                rows.append((name, f"HTTP {st}", "", "", str(d)[:80]))
                return
            ms.append(t)
            tokens = d.get("usage", {}).get("total_input_tokens")
        rows.append((name, f"{statistics.median(ms):.0f}", f"{pct(ms, 0.9):.0f}", str(tokens), ""))

    bench("text routing (4 q)", {"model": MODEL, "input": "Send Rahul a WhatsApp saying I'll be late",
                                  "questions": ROUTING_QUESTIONS})
    bench("text safety (4 q)", {"model": MODEL, "questions": SAFETY_QUESTIONS,
          "input": json.dumps({"app": "PhonePe", "button": "Pay Rs 500", "action": "click",
                               "request": "pay Rahul 500"})})

    if os.path.exists(a.image):
        b64 = base64.b64encode(open(a.image, "rb").read()).decode()
        mime = "image/png" if a.image.lower().endswith("png") else "image/jpeg"
        for res in ("low", "medium", "high"):
            bench(f"screenshot {res} (2 q)", {"model": MODEL, "questions": SCREEN_QUESTIONS, "input": [
                {"type": "image", "data": b64, "mime_type": mime, "resolution": res},
                {"type": "text", "text": "Step: opened the WhatsApp chat with Rahul."}]})
    else:
        print(f"(skipping screenshots, {a.image} not found)", file=sys.stderr)

    print("| Case | median ms | p90 ms | input tokens | note |\n|---|---|---|---|---|")
    for r in rows:
        print("| " + " | ".join(r) + " |")

    # Rate-limit probe: fire a burst back to back and count 429s.
    codes = [call(key, {"model": MODEL, "input": "hello", "questions": {"q": {"type": "noul", "instructions": "Is this a greeting?"}}})[0]
             for _ in range(a.burst)]
    print(f"\nBurst of {a.burst} sequential calls: {codes.count(200)} ok, {codes.count(429)} x 429, "
          f"{len(codes) - codes.count(200) - codes.count(429)} other")


if __name__ == "__main__":
    main()
