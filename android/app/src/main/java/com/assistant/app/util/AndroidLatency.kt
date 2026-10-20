package com.assistant.app.util

import android.os.SystemClock
import android.util.Log
import com.assistant.app.BuildConfig
import org.json.JSONObject
import java.util.UUID

/** Debug-only JSON lines, with no transcripts, provider call IDs, documents, or keys. */
fun newLatencySession(): LatencySession = LatencySession(
    id = UUID.randomUUID().toString(),
    clock = SystemClock::elapsedRealtime,
    sink = { fields -> Log.d("AssistantLatency", "LATENCY ${JSONObject(fields)}") },
    enabled = BuildConfig.DEBUG && !Log.isLoggable("AssistantLatencyOff", Log.DEBUG)
)
