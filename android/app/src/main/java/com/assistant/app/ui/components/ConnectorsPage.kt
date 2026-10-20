package com.assistant.app.ui.components

import android.Manifest
import android.app.AppOpsManager
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
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
import com.assistant.app.data.ToolGroup
import com.assistant.app.network.GithubDeviceCode
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentPink
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

private enum class Category(val label: String) {
    PRODUCTIVITY("Productivity"), DEVELOPER("Developer"), COMMUNICATION("Communication"),
    MEDIA("Media & location"), AUTOMATION("Automation")
}

/** Local connectors run on this phone; cloud connectors sign in to an online service. */
private enum class Place(val label: String) { LOCAL("Local"), CLOUD("Cloud") }

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
    val color: Color,
    val kind: Kind,
    val tools: List<DeviceTool> = emptyList()
)

private fun photosPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

private const val PHONE = "On your phone"

/** Connectors whose tools are picked out by hand; every other tool falls into its group's connector below. */
private val NamedConnectors = listOf(
    Connector("calendar", "Calendar", "Check your schedule, find free time and add events using your phone's calendars, Google Calendar included.",
        PHONE, Category.PRODUCTIVITY, Icons.Outlined.CalendarMonth, Color(0xFF4285F4),
        Kind.Local(listOf(Manifest.permission.READ_CALENDAR)),
        listOf(DeviceTool.UPCOMING_EVENTS, DeviceTool.CALENDAR_CONFLICTS, DeviceTool.CREATE_EVENT)),
    Connector("contacts", "Contacts", "Find people, call them and save new contacts from your phone's address book.",
        PHONE, Category.COMMUNICATION, Icons.Outlined.Contacts, Color(0xFF34A853),
        Kind.Local(listOf(Manifest.permission.READ_CONTACTS)),
        listOf(DeviceTool.SEARCH_CONTACT, DeviceTool.CALL_CONTACT, DeviceTool.CREATE_CONTACT, DeviceTool.CONTACT_BIRTHDAY)),
    Connector("notifications", "Notifications", "Read your current notifications and catch up on what you missed.",
        PHONE, Category.COMMUNICATION, Icons.Outlined.Notifications, Color(0xFFEA4335),
        Kind.Local(access = Access.NOTIFICATION_ACCESS), listOf(DeviceTool.READ_NOTIFICATIONS)),
    Connector("photos", "Photos", "Count and list your recent photos.",
        PHONE, Category.MEDIA, Icons.Outlined.PhotoLibrary, Color(0xFFF9AB00),
        Kind.Local(listOf(photosPermission())), listOf(DeviceTool.RECENT_PHOTOS)),
    Connector("location", "Location", "Know where you are and get directions.",
        PHONE, Category.MEDIA, Icons.Outlined.LocationOn, Color(0xFF0F9D58),
        Kind.Local(listOf(Manifest.permission.ACCESS_FINE_LOCATION)), listOf(DeviceTool.LOCATION, DeviceTool.DIRECTIONS))
)

/** Automation connectors: everything the assistant can do on the phone itself, one per tool group. */
private class GroupStyle(val name: String, val description: String, val icon: ImageVector, val color: Color, val access: Access = Access.NONE)

private val GroupStyles: Map<ToolGroup, GroupStyle> = mapOf(
    ToolGroup.DISPLAY_SOUND to GroupStyle("Display & sound", "Brightness, volume, ringer, vibration, wallpaper, rotation and media controls.", Icons.Outlined.Tune, Color(0xFF7E57C2)),
    ToolGroup.PHONE_INFO to GroupStyle("Phone info", "Battery, storage, network, Wi-Fi, Bluetooth, clipboard, flashlight and do not disturb.", Icons.Outlined.PhoneAndroid, Color(0xFF26A69A)),
    ToolGroup.APPS to GroupStyle("Apps", "Open apps, play music, open the camera and app settings.", Icons.Outlined.Apps, Color(0xFF42A5F5)),
    ToolGroup.ALARMS to GroupStyle("Alarms & timers", "Set alarms, timers and reminders.", Icons.Outlined.Alarm, Color(0xFFFF7043)),
    ToolGroup.CALLS to GroupStyle("Calls & email", "Place phone calls straight away and prepare email drafts (you press send).", Icons.Outlined.Call, Color(0xFF66BB6A)),
    ToolGroup.NOTES to GroupStyle("Notes", "Create, search and delete quick notes kept on this device.", Icons.Outlined.EditNote, Color(0xFFFFCA28)),
    ToolGroup.WEB to GroupStyle("Internet", "Test your internet speed.", Icons.Outlined.Language, Color(0xFF29B6F6)),
    ToolGroup.YOUTUBE to GroupStyle("YouTube", "Search videos, get details and transcripts, list a channel's videos and download videos or audio to Downloads/Ultron. Runs on the phone.", Icons.Outlined.PlayCircle, Color(0xFFFF0000)),
    ToolGroup.NAVIGATION to GroupStyle("Phone navigation", "Back, home, recents, notification shade, scrolling, lock screen and screenshots.", Icons.Outlined.Explore, Color(0xFF8D6E63), Access.ACCESSIBILITY),
    ToolGroup.SCREEN to GroupStyle("Screen control", "Read what is on screen, tap, type and wait for text. Sends screen text to Gemini.", Icons.Outlined.TouchApp, Color(0xFFEC407A), Access.ACCESSIBILITY),
    ToolGroup.HEALTH to GroupStyle("Sensors & health", "Step count and room light level.", Icons.Outlined.Favorite, Color(0xFFEF5350)),
    ToolGroup.NOTIFICATIONS to GroupStyle("App usage", "See which apps you use most. Sends app names to Gemini.", Icons.Outlined.QueryStats, Color(0xFF78909C), Access.USAGE_ACCESS)
)

private val DuckDuckGoConnector = Connector(
    "duckduckgo", "DuckDuckGo", "Search the web for the latest news, prices and facts.",
    "Free, no sign-in", Category.PRODUCTIVITY, Icons.Outlined.Language, Color(0xFFDE5833), Kind.Online,
    listOf(DeviceTool.WEB_SEARCH)
)

private val GitHubConnector = Connector(
    "github", "GitHub", "Ask about your repositories, issues, pull requests and code.",
    "Sign in with GitHub", Category.DEVELOPER, Icons.Outlined.Code, Color(0xFF6E5494), Kind.GitHub
)

private fun googleConnector(service: GoogleService, description: String, category: Category, icon: ImageVector, color: Color) =
    Connector(service.id, service.label, description, "Sign in with Google", category, icon, color, Kind.Google(service))

private val GoogleConnectors = listOf(
    googleConnector(GoogleService.CALENDAR, "See your schedule, find a free slot, create and answer events in Google Calendar.",
        Category.PRODUCTIVITY, Icons.Outlined.CalendarMonth, Color(0xFF4285F4)),
    googleConnector(GoogleService.GMAIL, "Search your inbox, read and summarize threads, and draft replies.",
        Category.COMMUNICATION, Icons.Outlined.Mail, Color(0xFFEA4335)),
    googleConnector(GoogleService.DRIVE, "Find, read and upload files in Google Drive.",
        Category.PRODUCTIVITY, Icons.Outlined.Folder, Color(0xFF34A853)),
    googleConnector(GoogleService.DOCS, "Read, summarize and edit your Google Docs.",
        Category.PRODUCTIVITY, Icons.Outlined.Description, Color(0xFF4285F4)),
    googleConnector(GoogleService.SHEETS, "Read, analyze and update your Google Sheets.",
        Category.PRODUCTIVITY, Icons.Outlined.TableChart, Color(0xFF0F9D58)),
    googleConnector(GoogleService.SLIDES, "Read and edit slides in your Google Slides presentations.",
        Category.PRODUCTIVITY, Icons.Outlined.Slideshow, Color(0xFFF9AB00))
)

private val Catalog: List<Connector> = run {
    val claimed = NamedConnectors.flatMap { it.tools }.toSet()
    val grouped = GroupStyles.mapNotNull { (group, style) ->
        val tools = DeviceTool.entries.filter { it.group == group && it !in claimed }
        if (tools.isEmpty()) null
        else Connector("group_${group.name}", style.name, style.description, PHONE, Category.AUTOMATION, style.icon, style.color,
            Kind.Local(access = style.access), tools)
    }
    NamedConnectors + DuckDuckGoConnector + GitHubConnector + GoogleConnectors + grouped
}

@Composable
fun ConnectorsPage(
    /** True for the Marketplace (find and add connectors); false for Connectors (the ones already added). */
    marketplace: Boolean,
    onBrowseToggle: () -> Unit,
    /** Ids of added connectors whose sign-in stopped working. */
    issues: Set<String>,
    githubLogin: String,
    deviceCode: GithubDeviceCode?,
    enabledTools: Set<DeviceTool>,
    onStartGithubSignIn: () -> Unit,
    onCancelGithubSignIn: () -> Unit,
    onDisconnectGithub: () -> Unit,
    githubTools: List<McpToolSpec>,
    enabledGithubTools: Set<String>,
    onGithubToolToggled: (String, Boolean) -> Unit,
    googleAccounts: Map<String, String>,
    googleTools: Map<String, List<McpToolSpec>>,
    enabledGoogleTools: Map<String, Set<String>>,
    onConnectGoogle: (GoogleService) -> Unit,
    onDisconnectGoogle: (GoogleService) -> Unit,
    onGoogleToolToggled: (GoogleService, String, Boolean) -> Unit,
    onToolsEnabled: (List<DeviceTool>, Boolean, String) -> Unit,
    onToolToggled: (DeviceTool, Boolean) -> Unit,
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
    var place by remember { mutableStateOf(Place.LOCAL) }
    var category by remember { mutableStateOf<Category?>(null) }
    var setupNeeded by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf<Connector?>(null) }

    fun accessGranted(kind: Kind.Local): Boolean = localAccessGranted(context, kind, resumeTick)

    fun connected(c: Connector): Boolean = when (val kind = c.kind) {
        is Kind.GitHub -> githubLogin.isNotEmpty()
        is Kind.Google -> kind.service.id in googleAccounts
        is Kind.Local -> c.tools.any { it in enabledTools } && accessGranted(kind)
        is Kind.Online -> c.tools.any { it in enabledTools }
    }

    /** An added online connector whose sign-in no longer works: it is shown with Reconnect instead of Connected. */
    fun needsReconnect(c: Connector): Boolean = connected(c) && c.kind !is Kind.Local && c.kind !is Kind.Online && c.id in issues

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
    // Marketplace: filter by category. Connectors: only what is already added, split into Local and Cloud.
    // Browse lists only what is still to add; the connectors already added live on the Connectors page.
    val shown = if (marketplace) matching.filter { (category == null || it.category == category) && !connected(it) }
    else matching.filter { connected(it) && (if (it.kind is Kind.Local) Place.LOCAL else Place.CLOUD) == place }

    @Composable
    fun Card(c: Connector) {
        val broken = needsReconnect(c)
        ConnectorCard(
            connector = c,
            connected = connected(c) && !broken,
            reconnect = broken,
            subtitle = when {
                broken -> "Sign-in stopped working"
                c.kind is Kind.GitHub && githubLogin.isNotEmpty() -> "Signed in as $githubLogin"
                c.kind is Kind.Google && c.kind.service.id in googleAccounts -> "Signed in as ${googleAccounts[c.kind.service.id]}"
                else -> c.subtitle
            },
            // The Marketplace only adds; per-tool switches and removal live on the Connectors page.
            rows = if (marketplace) emptyList() else toolRows(c, enabledTools, githubTools, enabledGithubTools, googleTools, enabledGoogleTools),
            connectLabel = if (marketplace) "Add" else "Connect",
            onRemove = { disconnecting = c },
            connectedLabel = if (marketplace) "Added" else "Connected",
            onConnect = { connect(c) },
            onDisconnect = { if (!marketplace) disconnecting = c },
            onRowToggled = { id, on ->
                when (val kind = c.kind) {
                    is Kind.GitHub -> onGithubToolToggled(id, on)
                    is Kind.Google -> onGoogleToolToggled(kind.service, id, on)
                    else -> DeviceTool.fromFunctionName(id)?.let { onToolToggled(it, on) }
                }
            },
            onAllToggled = { on ->
                when (val kind = c.kind) {
                    is Kind.GitHub -> githubTools.forEach { onGithubToolToggled(it.name, on) }
                    is Kind.Google -> googleTools[kind.service.id].orEmpty().forEach { onGoogleToolToggled(kind.service, it.name, on) }
                    else -> onToolsEnabled(c.tools, on, c.name)
                }
            }
        )
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (marketplace) "Browse connectors" else "Your connectors", color = AssistantTextPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pill(if (marketplace) "Done" else "Browse", filled = !marketplace, onClick = onBrowseToggle)
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip("All", category == null) { category = null }
                Category.entries.forEach { item -> CategoryChip(item.label, category == item) { category = item } }
            }
        } else Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp).clip(RoundedCornerShape(16.dp))
                .background(AssistantFieldBackground).border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Place.entries.forEach { item ->
                val on = item == place
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) AssistantSurface else Color.Transparent)
                        .clickable { place = item }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.label, color = if (on) AssistantAccent else AssistantTextSecondary, fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) {
                val empty = when {
                    marketplace && query.isBlank() -> "Everything here is already added."
                    marketplace || query.isNotBlank() -> "No connectors match your search."
                    else -> "Nothing added here yet. Tap Browse to add connectors."
                }
                Text(empty, color = AssistantTextMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
            if (marketplace) {
                if (shown.isNotEmpty()) SectionHeading("Available")
                shown.forEach { Card(it) }
            } else {
                var lastCategory: Category? = null
                shown.forEach { c ->
                    if (place == Place.LOCAL && (c.category == Category.AUTOMATION) != (lastCategory == Category.AUTOMATION) || lastCategory == null) {
                        SectionHeading(if (place == Place.CLOUD) "Online services" else if (c.category == Category.AUTOMATION) "Automation" else "Phone apps")
                    }
                    lastCategory = c.category
                    Card(c)
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
    Access.NONE -> kind.permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/** One switch under a connector: a device tool or a tool offered by a connected MCP server. */
private class ToolRow(val id: String, val label: String, val hint: String, val on: Boolean)

private fun toolRows(
    connector: Connector,
    enabledTools: Set<DeviceTool>,
    githubTools: List<McpToolSpec>,
    enabledGithubTools: Set<String>,
    googleTools: Map<String, List<McpToolSpec>>,
    enabledGoogleTools: Map<String, Set<String>>
): List<ToolRow> = when (val kind = connector.kind) {
    is Kind.GitHub -> mcpToolRows(githubTools, enabledGithubTools)
    is Kind.Google -> mcpToolRows(googleTools[kind.service.id].orEmpty(), enabledGoogleTools[kind.service.id].orEmpty())
    else -> connector.tools.map { ToolRow(it.functionName, it.label, it.hint, it in enabledTools) }
}

private fun mcpToolRows(tools: List<McpToolSpec>, enabled: Set<String>): List<ToolRow> = tools.map { tool ->
    val summary = tool.description.lineSequence().firstOrNull().orEmpty().take(110)
    ToolRow(tool.name, tool.name.replace('_', ' ').replaceFirstChar { it.uppercase() },
        if (tool.readOnly) summary else "Can change data. $summary", tool.name in enabled)
}

@Composable
private fun SectionHeading(text: String) {
    Text(text.uppercase(), color = AssistantTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp))
}

@Composable
private fun ConnectorCard(
    connector: Connector,
    connected: Boolean,
    subtitle: String,
    rows: List<ToolRow>,
    reconnect: Boolean,
    connectLabel: String,
    connectedLabel: String,
    onRemove: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRowToggled: (String, Boolean) -> Unit,
    onAllToggled: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(cardShape)
            .background(AssistantSurface).border(BorderStroke(1.dp, AssistantOutline), cardShape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(connector.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(connector.icon, contentDescription = null, tint = connector.color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(connector.name, color = AssistantTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
                Text(subtitle, color = AssistantTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            when {
                reconnect -> Pill("Reconnect", filled = true, onClick = onConnect)
                connected -> Pill(connectedLabel, filled = false, check = true, onClick = onDisconnect)
                else -> Pill(connectLabel, filled = true, onClick = onConnect)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(connector.description, color = AssistantTextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        if (reconnect) {
            TextButton(onClick = onRemove) { Text("Remove", color = AssistantTextSecondary, fontSize = 13.sp) }
        }
        if (rows.isNotEmpty()) {
            val on = rows.count { it.on }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tools · $on of ${rows.size} on", color = AssistantTextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = AssistantTextMuted)
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAllToggled(true) }, enabled = on < rows.size) {
                        Text("Select all", color = AssistantAccent, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onAllToggled(false) }, enabled = on > 0) {
                        Text("Clear", color = AssistantTextSecondary, fontSize = 13.sp)
                    }
                }
                rows.forEach { tool ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { onRowToggled(tool.id, !tool.on) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tool.on,
                            onCheckedChange = { onRowToggled(tool.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AssistantAccent,
                                uncheckedColor = AssistantTextMuted,
                                checkmarkColor = Color.White
                            )
                        )
                        Column(Modifier.weight(1f).padding(end = 4.dp)) {
                            Text(tool.label, color = AssistantTextPrimary, fontSize = 14.sp)
                            Text(tool.hint, color = AssistantTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) AssistantAccent.copy(alpha = 0.18f) else AssistantFieldBackground)
            .border(BorderStroke(1.dp, if (selected) AssistantAccent else AssistantOutline), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) AssistantAccent else AssistantTextSecondary, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, enabled: Boolean = true, check: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(38.dp).clip(shape)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(AssistantAccent, AssistantAccentPink)), shape)
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
