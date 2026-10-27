package com.assistant.app.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Finds an output the user connected on purpose (Bluetooth speaker, headphones, USB audio). */
object AudioRouting {

    private val EXTERNAL_TYPES: Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_USB_DEVICE)
        add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    /** Bluetooth SCO is left out on purpose: it only shows up once communication mode is on, so counting it
     *  would make the route flip back and forth as soon as the app changes the audio mode itself. */
    fun isExternalOutput(type: Int): Boolean = type in EXTERNAL_TYPES

    fun externalOutput(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { isExternalOutput(it.type) }
}
