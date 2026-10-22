package com.assistant.app.ui.components

import android.Manifest
import android.app.AppOpsManager
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Tune
import com.assistant.app.ui.theme.AppButtonShape
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.assistant.app.AssistantAccessibilityService
import com.assistant.app.BuildConfig
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.GoogleService
import com.assistant.app.data.McpToolSpec
import com.assistant.app.data.PhoneConnectors
import com.assistant.app.data.Surface
import com.assistant.app.network.GithubDeviceCode
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantAccentAlt
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

private enum class Category(val label: String) {
    PRODUCTIVITY("Productivity"), DEVELOPER("Developer"), COMMUNICATION("Communication"),
    MEDIA("Media & location"), PHONE("Phone"), AUTOMATION("Automation")
}

/** Extra Android access a connector needs before its tools work. */
private enum class Access { NONE, NOTIFICATION_ACCESS, ACCESSIBILITY, USAGE_ACCESS }

private sealed interface Kind {
    /** Tools that run on this phone; [permissions] and [access] are what Android must allow. */
    data class Local(val permissions: List<String> = emptyList(), val access: Access = Access.NONE) : Kind
    data object GitHub : Kind
    /** A Google Workspace service: adding it opens Google's account picker and permission screen. */
    data class Google(val service: GoogleService) : Kind
    /** An online service that needs no sign-in: connecting just switches its tools on. */
    data object Online : Kind
}

private class Connector(
    val id: String,
    val name: String,
    val description: String,
    val subtitle: String,
    val category: Category,
    val icon: ImageVector,
    val kind: Kind,
    val tools: List<DeviceTool> = emptyList(),
    /** Automation connectors act on the phone itself and work in voice only; every other connector works in voice and chat. */
    val automation: Boolean = false
)

private fun photosPermissions(): List<String> = when {
    // Android 14 lets the user share only selected photos, which is the separate "visual user selected" permission.
    Build.VERSION.SDK_INT >= 34 -> listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private const val PHONE = "On your phone"

/** A phone-side connector: its tools and whether it reaches the chat come from [PhoneConnectors]. */
private fun phone(
    id: String, name: String, description: String, category: Category, icon: ImageVector,
    kind: Kind = Kind.Local(), subtitle: String = PHONE
) = Connector(
    id, name, description, subtitle, category, icon, kind, PhoneConnectors.tools(id),
    automation = PhoneConnectors.surface(id) == Surface.VOICE
)

private val PhoneCatalog = listOf(
    phone("device_info", "Device info", "Time, battery, storage, network, Wi-Fi, Bluetooth, steps, light level and an internet speed test.",
        Category.PHONE, Icons.Outlined.PhoneAndroid),
    phone("device_controls", "Device controls", "Flashlight, brightness, volume, ringer, vibration, do not disturb, media keys, screen timeout and rotation.",
        Category.PHONE, Icons.Outlined.Tune),
    phone("alarms_notes", "Alarms & notes", "Set alarms, timers and reminders, and keep quick notes on this device.",
        Category.PRODUCTIVITY, Icons.Outlined.Alarm),
    phone("calendar", "Calendar", "Check your schedule, find free time and add events using your phone's calendars, Google Calendar included.",
        Category.PRODUCTIVITY, Icons.Outlined.CalendarMonth, Kind.Local(listOf(Manifest.permission.READ_CALENDAR))),
    phone("contacts", "Contacts", "Find people and see their birthdays from your phone's address book.",
        Category.COMMUNICATION, Icons.Outlined.Contacts, Kind.Local(listOf(Manifest.permission.READ_CONTACTS))),
    phone("notifications", "Notifications", "Read your current notifications and catch up on what you missed.",
        Category.COMMUNICATION, Icons.Outlined.Notifications, Kind.Local(access = Access.NOTIFICATION_ACCESS)),
    phone("photos", "Photos", "Count and list your recent photos.",
        Category.MEDIA, Icons.Outlined.PhotoLibrary, Kind.Local(photosPermissions())),
    phone("location", "Location", "Know where you are and get directions.",
        Category.MEDIA, Icons.Outlined.LocationOn, Kind.Local(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))),
    phone("app_usage", "App usage", "See which apps you use most. Sends app names to Gemini.",
        Category.PHONE, Icons.Outlined.QueryStats, Kind.Local(access = Access.USAGE_ACCESS)),
    phone("duckduckgo", "DuckDuckGo", "Search the web for the latest news, prices and facts.",
        Category.PRODUCTIVITY, Icons.Outlined.Language, Kind.Online, "Free, no sign-in"),
    phone("weather", "Weather", "Current weather and the forecast for where you are or any city.",
        Category.PRODUCTIVITY, Icons.Outlined.WbSunny, Kind.Online, "Free, no sign-in"),
    phone("youtube", "YouTube", "Search videos, get details and transcripts, list a channel's videos and download video or audio to Downloads/Ultron. Runs on the phone.",
        Category.MEDIA, Icons.Outlined.PlayCircle),
    // Automation connectors (voice only).
    phone("calls", "Calls", "Dial a number, call a contact by name and save new contacts.",
        Category.COMMUNICATION, Icons.Outlined.Call, Kind.Local(listOf(Manifest.permission.READ_CONTACTS))),
    phone("messages", "Messages", "Send a text message to a number or to a contact by name right away, without opening any screen.",
        Category.COMMUNICATION, Icons.Outlined.Forum, Kind.Local(listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS))),
    phone("apps_settings", "Apps & settings", "Open apps, the camera and settings, change the wallpaper, use the clipboard and show notifications.",
        Category.AUTOMATION, Icons.Outlined.Apps),
    phone("navigation", "Phone navigation", "Back, home, recents, notification shade, quick settings, scrolling, lock screen and screenshots.",
        Category.AUTOMATION, Icons.Outlined.Explore, Kind.Local(access = Access.ACCESSIBILITY)),
    phone("screen", "Screen control", "Read what is on screen, tap, type and wait for text. Sends screen text to Gemini.",
        Category.AUTOMATION, Icons.Outlined.TouchApp, Kind.Local(access = Access.ACCESSIBILITY))
)

private val GitHubConnector = Connector(
    "github", "GitHub", "Ask about your repositories, issues, pull requests and code.",
    "Sign in with GitHub", Category.DEVELOPER, Icons.Outlined.Code, Kind.GitHub
)

private fun googleConnector(service: GoogleService, description: String, category: Category, icon: ImageVector) =
    Connector(service.id, service.label, description, "Sign in with Google", category, icon, Kind.Google(service))

private val GoogleConnectors = listOf(
    googleConnector(GoogleService.CALENDAR, "See your schedule, find a free slot, create and answer events in Google Calendar.",
        Category.PRODUCTIVITY, Icons.Outlined.CalendarMonth),
    googleConnector(GoogleService.GMAIL, "Search your inbox, read and summarize threads, and draft replies.",
        Category.COMMUNICATION, Icons.Outlined.Mail),
    googleConnector(GoogleService.DRIVE, "Find, read and upload files in Google Drive.",
        Category.PRODUCTIVITY, Icons.Outlined.Folder),
    googleConnector(GoogleService.DOCS, "Read, summarize and edit your Google Docs.",
        Category.PRODUCTIVITY, Icons.Outlined.Description),
    googleConnector(GoogleService.SHEETS, "Read, analyze and update your Google Sheets.",
        Category.PRODUCTIVITY, Icons.Outlined.TableChart),
    googleConnector(GoogleService.SLIDES, "Read and edit slides in your Google Slides presentations.",
        Category.PRODUCTIVITY, Icons.Outlined.Slideshow)
)

private val Catalog: List<Connector> = PhoneCatalog + GitHubConnector + GoogleConnectors

@Composable
fun ConnectorsPage(
    /** True for the Marketplace (find and add connectors); false for Connectors (the ones already added). */
    marketplace: Boolean,
    /** Ids of added connectors whose sign-in stopped working. */
    issues: Set<String>,
    githubLogin: String,
    deviceCode: GithubDeviceCode?,
    enabledTools: Set<DeviceTool>,
    onStartGithubSignIn: () -> Unit,
    onCancelGithubSignIn: () -> Unit,
    onDisconnectGithub: () -> Unit,
    githubTools: List<McpToolSpec>,
    googleAccounts: Map<String, String>,
    googleTools: Map<String, List<McpToolSpec>>,
    onConnectGoogle: (GoogleService) -> Unit,
    onDisconnectGoogle: (GoogleService) -> Unit,
    onToolsEnabled: (List<DeviceTool>, Boolean, String) -> Unit,
    onRequestPermissions: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Android permission and special-access state changes outside the app, so re-read it on resume.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<Category?>(null) }
    var setupNeeded by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf<Connector?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    fun accessGranted(kind: Kind.Local): Boolean = localAccessGranted(context, kind, resumeTick)

    fun isConnected(c: Connector): Boolean = when (val kind = c.kind) {
        is Kind.GitHub -> githubLogin.isNotEmpty()
        is Kind.Google -> kind.service.id in googleAccounts
        // Added means its tools are on; a revoked permission leaves it listed so it can be repaired.
        is Kind.Local -> c.tools.any { it in enabledTools }
        is Kind.Online -> c.tools.any { it in enabledTools }
    }

    // Evaluated once per change of its inputs: accessGranted() queries the system, and the Marketplace lists the whole catalog.
    val connectedIds = remember(resumeTick, enabledTools, githubLogin, googleAccounts) {
        Catalog.filter { isConnected(it) }.map { it.id }.toSet()
    }
    fun connected(c: Connector): Boolean = c.id in connectedIds

    /** An added connector that no longer works (sign-in rejected, or Android access taken away): shown with Reconnect instead of Connected. */
    val reconnectIds = remember(connectedIds, issues, resumeTick) {
        Catalog.filter { c ->
            c.id in connectedIds && when (val kind = c.kind) {
                is Kind.Local -> !accessGranted(kind)
                is Kind.Online -> false
                else -> c.id in issues
            }
        }.map { it.id }.toSet()
    }
    fun needsReconnect(c: Connector): Boolean = c.id in reconnectIds

    fun connect(c: Connector) {
        when (val kind = c.kind) {
            is Kind.GitHub -> if (BuildConfig.GITHUB_CLIENT_ID.isNotBlank()) onStartGithubSignIn() else setupNeeded = true
            is Kind.Google -> onConnectGoogle(kind.service)
            is Kind.Online -> onToolsEnabled(c.tools, true, c.name)
            is Kind.Local -> {
                onToolsEnabled(c.tools, true, c.name)
                if (!accessGranted(kind)) {
                    val settingsAction = when (kind.access) {
                        Access.NOTIFICATION_ACCESS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        Access.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                        Access.USAGE_ACCESS -> Settings.ACTION_USAGE_ACCESS_SETTINGS
                        Access.NONE -> null
                    }
                    if (settingsAction != null) {
                        context.startActivity(Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        val missing = kind.permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isNotEmpty()) onRequestPermissions(missing)
                    }
                }
            }
        }
    }

    val matching = Catalog.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
    // Marketplace: every connector (never automation, which has its own section), filtered by category; the ones still to add come first.
    // Connectors: Installed (added, voice and chat) and Automation (voice only, always listed so it can be switched on).
    val shown = if (marketplace) matching.filter { !it.automation && (category == null || it.category == category) }
    else matching.filter { if (it.automation) true else connected(it) }

    val detail = detailId?.let { id -> Catalog.firstOrNull { it.id == id } }
    BackHandler(enabled = detail != null) { detailId = null }

    fun toolLines(c: Connector): List<ToolLine> = when (val kind = c.kind) {
        is Kind.GitHub -> githubTools.map { mcpLine(it) }
        is Kind.Google -> googleTools[kind.service.id].orEmpty().map { mcpLine(it) }
        else -> c.tools.map { ToolLine(it.label, it.hint, false) }
    }

    @Composable
    fun Card(c: Connector) {
        val broken = needsReconnect(c)
        ConnectorCard(
            connector = c,
            connected = connected(c) && !broken,
            reconnect = broken,
            subtitle = when {
                broken && c.kind is Kind.Local -> "Permission needed"
                broken -> "Sign-in stopped working"
                c.kind is Kind.GitHub && githubLogin.isNotEmpty() -> "Signed in as $githubLogin"
                c.kind is Kind.Google && c.kind.service.id in googleAccounts -> "Signed in as ${googleAccounts[c.kind.service.id]}"
                else -> c.subtitle
            },
            connectLabel = if (marketplace) "Add" else "Connect",
            onRemove = { disconnecting = c },
            connectedLabel = if (marketplace) "Added" else "Connected",
            onConnect = { connect(c) },
            onDisconnect = { if (marketplace) detailId = c.id else disconnecting = c },
            onOpen = { detailId = c.id }
        )
    }

    @Composable
    fun Section(title: String, items: List<Connector>) {
        if (items.isEmpty()) return
        SectionHeading(title)
        items.forEach { Card(it) }
    }

    if (detail != null) {
        val broken = needsReconnect(detail)
        ConnectorDetail(
            connector = detail,
            info = InfoById[detail.id],
            connected = connected(detail) && !broken,
            reconnect = broken,
            subtitle = when {
                broken && detail.kind is Kind.Local -> "Permission needed"
                detail.kind is Kind.GitHub && githubLogin.isNotEmpty() -> "Signed in as $githubLogin"
                detail.kind is Kind.Google && detail.kind.service.id in googleAccounts -> "Signed in as ${googleAccounts[detail.kind.service.id]}"
                else -> detail.subtitle
            },
            tools = toolLines(detail),
            connectLabel = if (marketplace) "Add" else "Connect",
            onConnect = { connect(detail) },
            onDisconnect = { disconnecting = detail },
            onBack = { detailId = null },
            modifier = modifier
        )
    } else Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (marketplace) "Marketplace" else "Your connectors", color = AssistantTextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search connectors", color = AssistantTextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AssistantTextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AssistantFieldBackground,
                unfocusedContainerColor = AssistantFieldBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AssistantAccent,
                focusedTextColor = AssistantTextPrimary,
                unfocusedTextColor = AssistantTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(18.dp))
        )
        if (marketplace) {
            val categories = Category.entries.filter { it != Category.AUTOMATION }
            ChoiceDropdown(
                options = listOf("all" to "All") + categories.map { it.name to it.label },
                selectedId = category?.name ?: "all",
                onSelect = { id -> category = categories.firstOrNull { it.name == id } },
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (marketplace) {
                if (shown.isEmpty()) {
                    Text("No connectors match your search.", color = AssistantTextMuted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    val (added, available) = shown.partition { connected(it) }
                    Section("Available", available)
                    Section("Added", added)
                }
            } else {
                val installed = shown.filter { !it.automation }
                val automation = shown.filter { it.automation }
                SectionHeading("Installed")
                if (installed.isEmpty()) {
                    Text(if (query.isBlank()) "Nothing installed yet. Open Marketplace from the menu to add connectors." else "No installed connector matches your search.",
                        color = AssistantTextMuted, modifier = Modifier.padding(vertical = 12.dp))
                }
                installed.forEach { Card(it) }
                if (automation.isNotEmpty()) {
                    SectionHeading("Automation connectors (voice only)")
                    automation.forEach { Card(it) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (deviceCode != null) GithubSignInDialog(deviceCode, onCancelGithubSignIn)
    if (setupNeeded) GithubSetupDialog { setupNeeded = false }
    disconnecting?.let { c ->
        AlertDialog(
            onDismissRequest = { disconnecting = null },
            title = { Text("Disconnect ${c.name}?") },
            text = {
                Text(
                    when (c.kind) {
                        is Kind.GitHub -> "The saved sign-in is removed from this device. You can connect again any time."
                        is Kind.Google -> "The account is removed from this app. You can add it again from Marketplace. To fully revoke access, remove the app in your Google account's security settings."
                        is Kind.Online -> "The assistant stops using this service."
                        is Kind.Local -> "The assistant stops using these tools. Android's own permission or access stays until you remove it in the phone's settings."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (c.kind) {
                        is Kind.GitHub -> onDisconnectGithub()
                        is Kind.Google -> onDisconnectGoogle(c.kind.service)
                        is Kind.Local, is Kind.Online -> onToolsEnabled(c.tools, false, c.name)
                                }
                    disconnecting = null
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { disconnecting = null }) { Text("Cancel") } }
        )
    }
}

private fun localAccessGranted(context: Context, kind: Kind.Local, @Suppress("UNUSED_PARAMETER") tick: Int): Boolean = when (kind.access) {
    Access.NOTIFICATION_ACCESS -> NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    Access.ACCESSIBILITY -> AssistantAccessibilityService.instance != null
    Access.USAGE_ACCESS -> {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }
    // Approximate location, or only the photos the user picked, is enough: any one of such a pair counts.
    Access.NONE -> if (Manifest.permission.ACCESS_COARSE_LOCATION in kind.permissions ||
        (Build.VERSION.SDK_INT >= 34 && Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in kind.permissions)
    ) {
        kind.permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    } else {
        kind.permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text.uppercase(), color = AssistantTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp))
}

/** Says where a connector works: Voice + Chat, or Voice only (automation). */
@Composable
private fun SurfaceChip(voiceOnly: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val textColor = if (voiceOnly) AssistantTextSecondary else AssistantAccent
    Text(
        if (voiceOnly) "Voice only" else "Voice + Chat",
        color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(shape)
            .background(if (voiceOnly) AssistantFieldBackground else AssistantAccent.copy(alpha = 0.14f))
            .border(BorderStroke(1.dp, textColor.copy(alpha = 0.35f)), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun ConnectorCard(
    connector: Connector,
    connected: Boolean,
    subtitle: String,
    reconnect: Boolean,
    connectLabel: String,
    connectedLabel: String,
    onRemove: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: () -> Unit
) {
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(cardShape).clickable(onClick = onOpen)
            .background(AssistantSurface).border(BorderStroke(1.dp, AssistantOutline), cardShape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AssistantSelectedBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(connector.icon, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(connector.name, color = AssistantTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                Text(subtitle, color = AssistantTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                SurfaceChip(voiceOnly = connector.automation)
            }
            Spacer(Modifier.size(8.dp))
            when {
                reconnect -> Pill(if (connector.kind is Kind.Local) "Repair" else "Reconnect", filled = true, onClick = onConnect)
                connected -> Pill(connectedLabel, filled = false, check = true, onClick = onDisconnect)
                else -> Pill(connectLabel, filled = true, onClick = onConnect)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(connector.description, color = AssistantTextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        if (reconnect) {
            TextButton(onClick = onRemove) { Text("Remove", color = AssistantTextSecondary, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text("View details", color = AssistantAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(18.dp))
        }
    }
}

/** One tool line on a connector's detail page. */
private class ToolLine(val title: String, val text: String, val changesData: Boolean)

private fun mcpLine(tool: McpToolSpec) = ToolLine(
    tool.name.replace('_', ' ').replaceFirstChar { it.uppercase() },
    tool.description.lineSequence().firstOrNull().orEmpty().take(140),
    !tool.readOnly
)

/** The long-form facts shown on a connector's own page. */
private class Info(val about: String, val needs: String, val privacy: String, val runsOn: String = "Your phone")

private const val GOOGLE_NEEDS = "Sign in with a Google account and approve Google's permission screen."
private const val GOOGLE_PRIVACY = "Your Google sign-in stays on this phone. The assistant's requests pass through your own Google Workspace server " +
    "on AWS Lambda, which only forwards them to Google and stores nothing. It includes tools that change your data."
private const val GOOGLE_RUNS = "Your Google Workspace server on AWS Lambda, then Google's own APIs"

private val InfoById: Map<String, Info> = mapOf(
    "device_info" to Info(
        "Lets the assistant tell you things about your phone: the date and time, battery, free storage and memory, network and Wi-Fi, Bluetooth, step count and room light. It only reads and never changes anything. The internet speed test downloads about 5 MB.",
        "No Android permission.",
        "Readings stay on the phone. They reach Gemini (or Azure OpenAI in the chat) only as part of the conversation when you ask."
    ),
    "device_controls" to Info(
        "Changes simple phone settings when you ask: the flashlight, screen brightness inside Ultron, volume, ringer mode, vibration, Do Not Disturb, music play, pause and skip, how long the screen stays on, and auto-rotate.",
        "Screen timeout and auto-rotate need Android's Modify system settings permission, and Do Not Disturb needs Do Not Disturb access. Android asks the first time you use them.",
        "Nothing leaves the phone except the words of the conversation."
    ),
    "alarms_notes" to Info(
        "Sets alarms and timers in the clock app, sends a reminder notification after the minutes you choose, and keeps quick notes. Notes are saved only on this phone.",
        "No permission. Reminders appear as notifications, so keep notifications on for Ultron.",
        "Notes never leave the phone unless you ask the assistant to read them out."
    ),
    "calendar" to Info(
        "Reads your upcoming events from every calendar on the phone (Google Calendar included), checks whether a time slot is free, and opens a new-event form for you to confirm. Nothing is saved until you press save.",
        "Calendar permission.",
        "Event titles and times are sent to the model when you ask about your schedule."
    ),
    "contacts" to Info(
        "Finds a contact's number by name and tells you their birthday. It only reads.",
        "Contacts permission.",
        "The contact details you ask about are sent to the model for that answer."
    ),
    "location" to Info(
        "Tells you where the phone is right now and starts navigation to a place in your maps app.",
        "Location permission.",
        "Your coordinates and address are sent to the model when you ask where you are."
    ),
    "photos" to Info(
        "Counts your recent photos and lists their file names. It does not open or send the pictures.",
        "Photos permission.",
        "Only file names and counts are shared with the model."
    ),
    "notifications" to Info(
        "Reads the notifications currently on your phone so the assistant can tell you what you missed.",
        "Notification access. A special Android screen opens when you add it.",
        "Notification text can be private. It is sent to the model whenever you ask. Add this only if you are comfortable with that."
    ),
    "app_usage" to Info(
        "Tells you which apps you use most and how long you used one app.",
        "Usage access. A special Android screen opens when you add it.",
        "App names and usage times are sent to the model when you ask."
    ),
    "duckduckgo" to Info(
        "Searches the web with DuckDuckGo and answers from the results, naming the source. There is no account and no API key.",
        "No sign-in and no permission.",
        "Your search words go to DuckDuckGo. Results are treated as untrusted text and never as instructions.",
        "Your phone, which asks DuckDuckGo directly"
    ),
    "weather" to Info(
        "Tells you the weather right now and the forecast for the next few days (temperature, conditions, chance of rain and wind) for where you are or for any city you name. There is no account and no API key.",
        "No sign-in. Using it without a city needs Location permission; Android asks the first time.",
        "A city name, or your coordinates when you ask about where you are, goes to Open-Meteo, a free weather service. The result is only numbers and conditions.",
        "Your phone, which asks Open-Meteo directly"
    ),
    "youtube" to Info(
        "Searches YouTube, shows video details, reads transcripts (captions), lists a channel's or playlist's videos, and downloads a video or only its audio to Downloads/Ultron. Before downloading it asks which format and quality you want, from what that video really offers (up to 4K or 8K). It all runs on your phone because YouTube blocks cloud servers.",
        "No sign-in. Downloads need free storage (a 4K video can be several GB), and a notification shows the progress.",
        "Requests go from your phone straight to YouTube. Saving videos may be against YouTube's terms of service, so download only what you are allowed to keep.",
        "Your phone, which asks YouTube directly"
    ),
    "github" to Info(
        "Lets the assistant work with your GitHub: search repositories and code, read files, issues, pull requests and commits, and make changes with the tools GitHub offers. Every tool GitHub's server provides is used, including ones that change data.",
        "Sign in with GitHub. A code is copied and GitHub opens in your browser.",
        "Requests go to GitHub's own MCP server with your sign-in token, which is kept encrypted on this phone. Repository text is untrusted and never treated as instructions.",
        "GitHub's MCP server"
    ),
    "google_calendar" to Info("See your calendars and events, find a free slot, and create, answer and delete events.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "gmail" to Info("Search your inbox, read messages, list labels and create drafts. Drafts are never sent automatically.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "google_drive" to Info("Search files, list recent files, read file contents and create new files.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "google_docs" to Info("Read your Google Docs, add text to them and replace text in them.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "google_sheets" to Info("Read cell values, and update or append rows in your Google Sheets.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "google_slides" to Info("Read your Google Slides presentations and replace text in them.", GOOGLE_NEEDS, GOOGLE_PRIVACY, GOOGLE_RUNS),
    "calls" to Info(
        "Places a phone call to a number or to a contact by name right away, and opens a new-contact form for you to save. It works in voice and in the chat.",
        "Contacts permission. Android asks for the Phone permission the first time a call is placed.",
        "A call is placed only when you ask. The number or name is part of the conversation."
    ),
    "messages" to Info(
        "Sends a text message (SMS) to a number or to a contact by name as soon as you ask, with no screen to confirm on. It sends only what you said, one message per request, up to 480 characters, and at most 10 messages an hour. It refuses very short numbers, which can be paid services, and if several contacts match it asks you which one. It works in voice and in the chat.",
        "SMS permission and Contacts permission. Android asks the first time a message is sent.",
        "Your carrier may charge for SMS as usual. The recipient and the text are part of the conversation with the model."
    ),
    "apps_settings" to Info(
        "Opens apps by name, the camera, Android settings and an app's info page, changes the wallpaper from an image link, reads or writes the clipboard and posts a notification. It works by voice only.",
        "No special permission. The wallpaper tool needs an internet link to an image.",
        "Clipboard text you ask the assistant to read is sent to the model."
    ),
    "navigation" to Info(
        "Presses Back, Home and Recents, opens the notification shade and quick settings, scrolls, locks the screen and takes screenshots, like a person using the phone. It works by voice only.",
        "Accessibility access for Ultron. Android's accessibility screen opens when you add it.",
        "It sends taps and gestures to your phone only. It does not read what is on the screen."
    ),
    "screen" to Info(
        "Reads the buttons and text on the screen, taps them, types and waits for text, so the assistant can use other apps for you. It works by voice only.",
        "Accessibility access for Ultron. Android's accessibility screen opens when you add it.",
        "Screen text is sent to Gemini whenever the assistant reads the screen, and it may include private information. Use it with care."
    )
)

/** A connector's own page: what it does, where it runs, what it needs, where data goes and which tools it has. */
@Composable
private fun ConnectorDetail(
    connector: Connector,
    info: Info?,
    connected: Boolean,
    reconnect: Boolean,
    subtitle: String,
    tools: List<ToolLine>,
    connectLabel: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AssistantTextPrimary)
            }
            Text("Connector details", color = AssistantTextSecondary, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(AssistantSelectedBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(connector.icon, contentDescription = null, tint = AssistantAccent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(connector.name, color = AssistantTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
                    Text(subtitle, color = AssistantTextMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SurfaceChip(voiceOnly = connector.automation)
                Spacer(Modifier.weight(1f))
                when {
                    reconnect -> Pill(if (connector.kind is Kind.Local) "Repair" else "Reconnect", filled = true, onClick = onConnect)
                    connected -> Pill("Disconnect", filled = false, onClick = onDisconnect)
                    else -> Pill(connectLabel, filled = true, onClick = onConnect)
                }
            }
            DetailSection("About", info?.about ?: connector.description)
            DetailSection(
                "Works in",
                if (connector.automation) "Voice only. Automation connectors act on your phone, so they never run from the typed chat."
                else "Voice and the typed chat."
            )
            DetailSection("Runs on", info?.runsOn ?: "Your phone")
            info?.let {
                DetailSection("What it needs", it.needs)
                DetailSection("Your data", it.privacy)
            }
            SectionHeading("Tools" + if (tools.isNotEmpty()) " (${tools.size})" else "")
            Text(
                "When this connector is added, the assistant can use all of these tools.",
                color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            if (tools.isEmpty()) {
                Text("The tool list appears here after you sign in.", color = AssistantTextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            tools.forEach { tool ->
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(shape).background(AssistantSurface)
                        .border(BorderStroke(1.dp, AssistantOutline), shape).padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(tool.title, color = AssistantTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (tool.text.isNotBlank()) {
                        Text(tool.text, color = AssistantTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    if (tool.changesData) {
                        Text("Can change data", color = AssistantAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    SectionHeading(title)
    Text(body, color = AssistantTextSecondary, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

@Composable
private fun Pill(text: String, filled: Boolean, enabled: Boolean = true, check: Boolean = false, onClick: () -> Unit) {
    val shape = AppButtonShape
    Row(
        Modifier.height(38.dp).clip(shape)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(AssistantAccentFill, AssistantAccentAlt)), shape)
                else Modifier.border(BorderStroke(1.dp, AssistantOutline), shape)
            )
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = if (check) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (check) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = AssistantTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text,
            color = when {
                filled -> Color.White
                enabled -> AssistantTextSecondary
                else -> AssistantTextMuted
            },
            fontSize = if (check) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Shown while the user approves the sign-in on github.com in the phone's browser. */
@Composable
private fun GithubSignInDialog(code: GithubDeviceCode, onCancel: () -> Unit) {
    val context = LocalContext.current
    // Copy the code and open GitHub's approval page once, so the user only has to paste and confirm.
    LaunchedEffect(code.userCode) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("GitHub code", code.userCode))
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Sign in with GitHub") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GitHub opened in your browser. Paste or type this code there and approve.",
                    color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(code.userCode, color = AssistantTextPrimary, fontSize = 30.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AssistantAccent)
                    Spacer(Modifier.size(10.dp))
                    Text("Waiting for approval...", color = AssistantTextMuted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Open GitHub again") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

/** Shown when this build has no GitHub client id, so browser sign-in cannot start yet. */
@Composable
private fun GithubSetupDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub sign-in is not set up") },
        text = {
            Text(
                "Browser sign-in needs a GitHub OAuth App client id, which this build does not have yet. " +
                    "Add GITHUB_CLIENT_ID to the app's local.properties and rebuild.",
                color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 19.sp
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
