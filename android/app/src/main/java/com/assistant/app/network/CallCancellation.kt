package com.assistant.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import okhttp3.Call

/**
 * Cancels [call] the moment the calling coroutine is cancelled, even while a blocking `execute()` or body read
 * is still running on its thread. Cancel the returned job when the call is finished.
 */
internal suspend fun watchCancellation(call: Call): Job =
    CoroutineScope(currentCoroutineContext()).launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            call.cancel()
        }
    }
