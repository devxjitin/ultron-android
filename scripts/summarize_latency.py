"""Summarize debug LATENCY JSON lines without emitting conversation content.

Usage: python summarize_latency.py <captured-logcat.txt>
Only completed, RMS-anchored turns enter speech-to-playback percentiles.
Classify language/fresh/repeated test blocks externally; queries are never logged.
"""

import collections
import json
import math
import statistics
import sys


def read_events(lines):
    for line in lines:
        if "LATENCY " not in line:
            continue
        try:
            event = json.loads(line.split("LATENCY ", 1)[1])
        except (ValueError, TypeError):
            continue
        if isinstance(event, dict) and "event" in event and "session" in event:
            yield event


def stats(values):
    values = sorted(values)
    if not values:
        return "n=0"
    return (f"n={len(values)} median={statistics.median(values):.1f}ms "
            f"p95={values[math.ceil(len(values) * .95) - 1]:.1f}ms"
            + (" (small sample)" if len(values) < 20 else ""))


def summarize(events):
    events = list(events)
    summaries = {(e["session"], e["turn"]): e for e in events
                 if e["event"] == "turn_summary" and "turn" in e}
    starts = {(e["session"], e["turn"]) for e in events
              if e["event"] == "turn_start" and "turn" in e}
    print(f"Turns started={len(starts)} summarized={len(summaries)} "
          f"incomplete={len(starts - summaries.keys())}")
    outcomes = collections.Counter(e.get("outcome", "unknown") for e in summaries.values())
    print("Outcomes: " + ", ".join(f"{k}={v}" for k, v in sorted(outcomes.items())))
    embedding_outcomes = collections.Counter(e.get("outcome", "unknown") for e in events
                                            if e["event"] == "embedding_end")
    print("Embedding outcomes: " + ", ".join(f"{k}={v}" for k, v in sorted(embedding_outcomes.items())))
    for knowledge in (False, True):
        group = [e for e in summaries.values() if e.get("knowledge") == knowledge
                 and e.get("outcome") == "completed"]
        acoustic = [e["speech_to_playback_ms"] for e in group
                    if e.get("speech_source") == "rms" and "speech_to_playback_ms" in e]
        label = "knowledge" if knowledge else "general"
        print(f"{label} speech-to-playback estimate: {stats(acoustic)}; "
              f"excluded missing/non-RMS anchor={len(group) - len(acoustic)}")
        print(f"{label} received-audio-to-playback: " +
              stats([e["audio_to_playback_ms"] for e in group if "audio_to_playback_ms" in e]))
    valid = {key for key, e in summaries.items() if e.get("outcome") == "completed"}
    for stage in ("chunks_loaded", "embedding_end", "ranking_complete", "tool_response_sent"):
        values = [e["duration_ms"] for e in events if e["event"] == stage
                  and (e["session"], e.get("turn")) in valid and "duration_ms" in e
                  and e.get("outcome", "completed") == "completed"]
        print(f"{stage}: {stats(values)}")
    # Per-tool total and per-model embedding samples are not additive turn percentiles.
    sent = {}
    continuation = []
    for e in sorted(events, key=lambda e: e.get("at_ms", 0)):
        key = (e["session"], e.get("turn"))
        if e["event"] == "tool_response_sent" and e.get("accepted"):
            sent[key] = e["at_ms"]
        if e["event"] == "first_audio_received" and key in valid and key in sent:
            continuation.append(e["at_ms"] - sent[key])
    print("last-tool-response-to-first-audio: " + stats(continuation))
    by_session = collections.defaultdict(dict)
    for e in events:
        if e["event"] in ("connect_start", "setup_ready", "stream_start", "stream_ready", "session_origin"):
            by_session[e["session"]][e["event"]] = e
    for mode in ("cold", "prewarm", "reconnect"):
        values = []
        for data in by_session.values():
            if data.get("session_origin", {}).get("mode") != mode:
                continue
            if "connect_start" in data and "setup_ready" in data:
                values.append(data["setup_ready"]["at_ms"] - data["connect_start"]["at_ms"])
        print(f"{mode} connect-to-setup: {stats(values)}")
    for reused in (False, True):
        values = [data["stream_ready"]["at_ms"] - data["stream_start"]["at_ms"]
                  for data in by_session.values() if "stream_ready" in data and "stream_start" in data
                  and data["stream_start"].get("reused_warm") == reused]
        print(f"reused-warm={reused} activation-to-stream: {stats(values)}")
    print("Mid-reply underrun counter increments: " + str(sum(
        e.get("underruns_delta", 0) for e in events
        if e["event"] == "audio_underruns" and e.get("mid_reply"))))
    print("Playback progress is an estimate, not acoustic onset. "
          "Compare matching language/query/build/network test blocks separately.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python summarize_latency.py <captured-logcat.txt>")
    with open(sys.argv[1], encoding="utf-8-sig", errors="replace") as source:
        summarize(read_events(source))
