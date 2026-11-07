package com.assistant.app.data

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.assistant.app.AssistantAccessibilityService
import com.assistant.app.AssistantNotificationListener

/** How far an Android special access (accessibility, notification listener) is from working. */
enum class AccessState { GRANTED, ENABLED_NOT_CONNECTED, OFF }

/** One place that answers "is this access really on", shared by the Connectors page and the tool runner. */
object AccessChecks {
    fun accessibility(context: Context): AccessState {
        if (AssistantAccessibilityService.instance != null) return AccessState.GRANTED
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val mine = ComponentName(context, AssistantAccessibilityService::class.java)
        val listed = enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { name -> name == mine } == true
        }
        return if (listed) AccessState.ENABLED_NOT_CONNECTED else AccessState.OFF
    }

    fun notificationListener(context: Context): AccessState {
        if (AssistantNotificationListener.instance != null) return AccessState.GRANTED
        return if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            AccessState.ENABLED_NOT_CONNECTED
        } else AccessState.OFF
    }

    /** Every file on the phone: All files access from Android 11, the storage permission before that. */
    fun allFiles(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Usage access. Some phones report MODE_DEFAULT even when it is on, so the permission itself is the tie-breaker. */
    fun usageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        return mode == AppOpsManager.MODE_DEFAULT &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    }
}
