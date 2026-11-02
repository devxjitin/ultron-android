package com.assistant.app.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assistant.app.data.AccessGate
import com.assistant.app.data.BillingPlan
import com.assistant.app.data.SubscriptionCache
import com.assistant.app.data.SubscriptionStatus
import com.assistant.app.data.isUsableOffline
import com.assistant.app.network.BillingClient
import com.assistant.app.network.BillingException
import com.assistant.app.network.GoogleAuth
import com.assistant.app.network.PendingSubscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user stands before the assistant opens: signed in with Google, then a paid subscription. */
sealed interface AccessState {
    data object Checking : AccessState
    data class SignedOut(val busy: Boolean = false, val error: String? = null) : AccessState
    data class Locked(
        val email: String,
        val status: SubscriptionStatus,
        /** The plan whose payment is being set up or checked right now. */
        val busyPlan: BillingPlan? = null,
        val error: String? = null
    ) : AccessState
    data class Active(val email: String, val status: SubscriptionStatus) : AccessState
}

/**
 * Keeps the app locked until the signed-in Google user has an active subscription. The server decides; the last answer it
 * gave is cached so a paid user can still open the app offline until the paid cycle ends.
 */
class AccessViewModel(application: Application) : AndroidViewModel(application) {
    private val billing = BillingClient()
    private val cache = SubscriptionCache(application)
    private val _state = MutableStateFlow<AccessState>(AccessState.Checking)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    /** The subscription waiting for Checkout to finish, so its result can be matched to it. */
    private var pending: PendingSubscription? = null

    init {
        refresh()
    }

    /** Re-reads the subscription; [quiet] keeps the current screen while it loads, for example when the app comes back. */
    fun refresh(quiet: Boolean = false) {
        val user = GoogleAuth.currentGoogleUser()
        if (user == null) {
            setState(AccessState.SignedOut(error = if (GoogleAuth.isConfigured) null else NOT_CONFIGURED))
            return
        }
        val email = user.email.orEmpty()
        val cached = cache.load(user.uid)
        val usableOffline = isUsableOffline(cached, nowSeconds())
        if (usableOffline) setState(AccessState.Active(email, cached!!))
        else if (!quiet || _state.value !is AccessState.Active) setState(AccessState.Checking)
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { billing.status() }
                cache.save(user.uid, status)
                setState(if (status.active) AccessState.Active(email, status) else AccessState.Locked(email, status))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Subscription check failed", e)
                if (!usableOffline) {
                    setState(AccessState.Locked(email, cached ?: SubscriptionStatus.NONE, error = "Couldn't reach the server. Check your internet and try again."))
                }
            }
        }
    }

    fun signIn(activity: Activity) {
        if (!GoogleAuth.isConfigured) return
        setState(AccessState.SignedOut(busy = true))
        viewModelScope.launch {
            try {
                GoogleAuth.signIn(activity)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google sign-in failed", e)
                setState(AccessState.SignedOut(error = "Google sign-in didn't finish. Please try again."))
            }
        }
    }

    fun signOut(activity: Activity) {
        viewModelScope.launch {
            GoogleAuth.signOut(activity)
            cache.clear()
            pending = null
            setState(AccessState.SignedOut())
        }
    }

    /** Creates the Razorpay subscription for [plan] and hands it to [openCheckout] to collect the payment. */
    fun buy(plan: BillingPlan, openCheckout: (PendingSubscription, String) -> Unit) {
        val locked = _state.value as? AccessState.Locked ?: return
        if (locked.busyPlan != null) return
        setState(locked.copy(busyPlan = plan, error = null))
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) { billing.subscribe(plan) }
                pending = created
                openCheckout(created, locked.email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BillingException) {
                if (e.code == 409) refresh() else failPayment("Couldn't start the payment. Please try again.")
            } catch (e: Exception) {
                Log.w(TAG, "Subscription create failed", e)
                failPayment("Couldn't start the payment. Check your internet and try again.")
            }
        }
    }

    /** Checkout says the payment went through; the server checks the signature with Razorpay before unlocking. */
    fun onPaymentSuccess(paymentId: String, subscriptionId: String?, signature: String?) {
        val id = subscriptionId?.takeIf { it.isNotBlank() } ?: pending?.subscriptionId
        if (id == null || signature.isNullOrBlank()) {
            // The charge happened but Checkout lost the details; the webhook still records it, so ask the server.
            refresh()
            return
        }
        val user = GoogleAuth.currentGoogleUser() ?: return refresh()
        viewModelScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { billing.verify(paymentId, id, signature) }
                pending = null
                cache.save(user.uid, status)
                setState(
                    if (status.active) AccessState.Active(user.email.orEmpty(), status)
                    else AccessState.Locked(user.email.orEmpty(), status,
                        error = "Payment received. Your plan will be active in a minute; tap Refresh.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Payment verify failed", e)
                failPayment("Payment received but not confirmed yet. Tap Refresh in a minute.")
            }
        }
    }

    fun onPaymentFailed(cancelled: Boolean) {
        pending = null
        failPayment(if (cancelled) null else "Payment didn't go through. Please try again.")
    }

    private fun failPayment(message: String?) {
        _state.update { if (it is AccessState.Locked) it.copy(busyPlan = null, error = message) else it }
        AccessGate.unlocked = _state.value is AccessState.Active
    }

    private fun setState(state: AccessState) {
        AccessGate.unlocked = state is AccessState.Active
        _state.value = state
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private companion object {
        const val TAG = "Access"
        const val NOT_CONFIGURED = "Sign-in is not set up in this build."
    }
}
