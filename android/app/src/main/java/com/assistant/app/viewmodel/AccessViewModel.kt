package com.assistant.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.app.data.AccessGate
import com.assistant.app.data.LicenseKey
import com.assistant.app.data.LicenseStatus
import com.assistant.app.data.LicenseStore
import com.assistant.app.data.isUsableOffline
import com.assistant.app.network.LicenseClient
import com.assistant.app.network.LicenseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user stands before the assistant opens: an activation key bought on the website store. */
sealed interface AccessState {
    data object Checking : AccessState
    data class NeedsKey(val busy: Boolean = false, val error: String? = null) : AccessState
    data class Active(val key: String, val status: LicenseStatus) : AccessState
}

/**
 * Keeps the app locked until an activation key is confirmed for this device. The server decides; the last answer it gave
 * is cached so the app still opens offline for a while (see [isUsableOffline]).
 */
class AccessViewModel(application: Application) : AndroidViewModel(application) {
    private val client = LicenseClient()
    private val store = LicenseStore(application)
    private val _state = MutableStateFlow<AccessState>(AccessState.Checking)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    // At most one check and one activation run at a time. Removing the key or typing a new one cancels what is in
    // flight, so a late answer for an old key can never save it again or unlock the app.
    private var refreshJob: Job? = null
    private var activateJob: Job? = null

    init {
        refresh()
    }

    /** Re-checks the saved key; [quiet] keeps the current screen while it loads, for example when the app comes back. */
    fun refresh(quiet: Boolean = false) {
        if (activateJob?.isActive == true) return
        refreshJob?.cancel()
        val key = store.key
        if (key == null) {
            setState(AccessState.NeedsKey())
            return
        }
        val cached = store.lastStatus()
        val usableOffline = isUsableOffline(cached, store.checkedAt, nowSeconds())
        if (usableOffline) setState(AccessState.Active(key, cached!!))
        else if (!quiet || _state.value !is AccessState.Active) setState(AccessState.Checking)
        refreshJob = viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { client.status(key) }
                if (store.key != key) return@launch
                store.save(key, status, nowSeconds())
                setState(if (status.active) AccessState.Active(key, status) else AccessState.NeedsKey(error = messageFor(status.reason)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A refused or failed request (throttled, server error, offline) is not an answer about the key, so the
                // cached one keeps counting; inactive keys come back as a normal reply with a reason.
                Log.w(TAG, "License check failed", e)
                if (!usableOffline) setState(AccessState.NeedsKey(error = OFFLINE))
            }
        }
    }

    /** Checks a key the user typed or pasted and, when the server accepts it, binds it to this device. */
    fun activate(input: String) {
        if ((_state.value as? AccessState.NeedsKey)?.busy == true) return
        val key = LicenseKey.normalize(input)
        if (key == null) {
            setState(AccessState.NeedsKey(error = "That doesn't look like an activation key. It starts with ULTR-."))
            return
        }
        refreshJob?.cancel()
        setState(AccessState.NeedsKey(busy = true))
        activateJob = viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { client.activate(key) }
                if (status.active) {
                    store.save(key, status, nowSeconds())
                    setState(AccessState.Active(key, status))
                } else {
                    setState(AccessState.NeedsKey(error = messageFor(status.reason)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LicenseException) {
                setState(AccessState.NeedsKey(error = messageFor(e.message.orEmpty())))
            } catch (e: Exception) {
                Log.w(TAG, "Activation failed", e)
                setState(AccessState.NeedsKey(error = OFFLINE))
            }
        }
    }

    /** Forgets the key on this phone only; it stays valid and can be activated again here or elsewhere. */
    fun removeKey() {
        refreshJob?.cancel()
        activateJob?.cancel()
        store.clear()
        setState(AccessState.NeedsKey())
    }

    private fun setState(state: AccessState) {
        AccessGate.unlocked = state is AccessState.Active
        _state.value = state
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private companion object {
        const val TAG = "Access"
        const val OFFLINE = "Couldn't reach the server. Check your internet and try again."

        fun messageFor(reason: String): String = when (reason) {
            "unknown_key" -> "This key isn't valid. Check it against the email from the store."
            "expired" -> "This key has expired. Buy a new one on the website store."
            "revoked" -> "This key was switched off. Contact support if that's a mistake."
            "moved" -> "This key is now active on another phone. Enter it again to use it here."
            "moved_recently" -> "This key moved to another phone in the last day. Try again tomorrow."
            "bad_key" -> "That doesn't look like an activation key. It starts with ULTR-."
            else -> "Couldn't activate the key. Please try again."
        }
    }
}
