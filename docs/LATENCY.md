# Measuring voice latency

The debug build logs one `LATENCY` JSON line per event (tag `AssistantLatency`). To get a baseline:

1. Install the debug build and run `adb logcat -s AssistantLatency > latency.txt`.
2. Hold 20 or more normal turns: a mix of short commands, a web question and a tool action, in the language you use.
3. Run `python scripts/summarize_latency.py latency.txt`.

The number to watch is speech end to first audio (p50 and p95). Take it before and after any change to the setup message, the tool path or the VAD settings, and keep a note of both.
