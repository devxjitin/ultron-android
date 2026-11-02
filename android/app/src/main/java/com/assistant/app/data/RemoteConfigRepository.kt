package com.assistant.app.data

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current [RemoteSettings]. It starts from the bundled defaults, so the app behaves the same offline and on
 * first launch. [refresh] pulls the latest Remote Config values and publishes them; a failure just keeps what we have.
 */
object RemoteConfigRepository {
    private const val TAG = "RemoteConfig"
    private const val FETCH_INTERVAL_SECONDS = 3_600L

    private val state = MutableStateFlow(RemoteSettings.DEFAULTS)
    val settings: StateFlow<RemoteSettings> = state.asStateFlow()

    /** Latest settings for code that is not collecting the flow, such as the tool runner. */
    val current: RemoteSettings get() = state.value

    fun refresh() {
        runCatching {
            val config = FirebaseRemoteConfig.getInstance()
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS).build()
            )
            apply(config)
            config.fetchAndActivate()
                .addOnSuccessListener { apply(config) }
                .addOnFailureListener { Log.w(TAG, "Fetch failed, keeping current values", it) }
        }.onFailure { Log.w(TAG, "Remote Config unavailable", it) }
    }

    private fun apply(config: FirebaseRemoteConfig) {
        state.value = RemoteSettings.from { key ->
            val value = config.getValue(key)
            if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else value.asString()
        }
    }
}
