package com.assistant.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.assistant.app.data.OwnClock
import com.assistant.app.ui.theme.AssistantTheme

/** The screen shown over the lock screen while an alarm or timer rings: what it is, Stop, and Snooze for alarms. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AssistantTheme {
                val ring by AlarmRingService.current.collectAsState()
                val shown = ring
                // Stopped from the notification, by voice or by the time limit: the screen has nothing left to show.
                if (shown == null) LaunchedEffect(Unit) { finish() }
                else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val timer = shown.kind == OwnClock.KIND_TIMER
                        Text(if (timer) "Timer" else "Alarm", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (timer) "Time is up" else OwnClock.clockTime(shown.startedAt),
                            style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center
                        )
                        if (shown.label.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(shown.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = { AlarmRingService.stop(this@AlarmActivity) }, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                            Text("Stop", style = MaterialTheme.typography.titleLarge)
                        }
                        if (!timer) {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { AlarmRingService.snooze(this@AlarmActivity) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                                Text("Snooze ${AlarmRingService.SNOOZE_MINUTES} min")
                            }
                        }
                    }
                }
            }
        }
    }
}
