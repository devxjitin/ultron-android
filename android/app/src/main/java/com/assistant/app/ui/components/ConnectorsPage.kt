package com.assistant.app.ui.components

import android.Manifest
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Extension
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
import com.assistant.app.ui.theme.CardShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.assistant.app.data.AccessChecks
import com.assistant.app.data.AccessState
import com.assistant.app.BuildConfig
import com.assistant.app.data.DeviceTool
import com.assistant.app.data.ConnectorCategory
import com.assistant.app.data.ConnectorInfo
import com.assistant.app.data.ConnectorKind
import com.assistant.app.data.ConnectorRegistry
import com.assistant.app.data.SpecialAccess
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantAccentAlt
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

private typealias Access = SpecialAccess

private sealed interface Kind {
    /** Tools that run on this phone; [permissions] and [access] are what Android must allow. */
    data class Local(val permissions: List<String> = emptyList(), val access: Access = Access.NONE) : Kind
    /** An online service that needs no sign-in: connecting just switches its tools on. */
    data object Online : Kind
}

private class Connector(
    val id: String,
    val name: String,
    val description: String,
    val subtitle: String,
    val icon: ImageVector,
    val kind: Kind,
    val category: ConnectorCategory,
    val tools: List<DeviceTool> = emptyList()
)

/** Icons are a screen concern, so they live here; everything else about a connector comes from [ConnectorRegistry]. */
private val ConnectorIcons: Map<String, ImageVector> = mapOf(
    "device_info" to Icons.Outlined.PhoneAndroid,
    "device_controls" to Icons.Outlined.Tune,
    "alarms" to Icons.Outlined.Alarm,
    "reminders" to Icons.Outlined.Schedule,
    "calendar" to Icons.Outlined.CalendarMonth,
    "contacts" to Icons.Outlined.Contacts,
    "calls" to Icons.Outlined.Call,
    "notifications" to Icons.Outlined.Notifications,
    "location" to Icons.Outlined.LocationOn,
    "app_usage" to Icons.Outlined.QueryStats,
    "duckduckgo" to Icons.Outlined.Language,
    "web_reader" to Icons.Outlined.Description,
    "weather" to Icons.Outlined.WbSunny,
    "youtube" to Icons.Outlined.PlayCircle,
    "youtube_downloads" to Icons.Outlined.Download,
    "apps_settings" to Icons.Outlined.Apps,
    "files" to Icons.Outlined.Folder,
    "images" to Icons.Outlined.Image,
    "navigation" to Icons.Outlined.Explore,
    "screen" to Icons.Outlined.TouchApp
)

private val Catalog: List<Connector> = ConnectorRegistry.all.map { spec ->
    Connector(
        id = spec.id, name = spec.name, description = spec.description, subtitle = spec.subtitle,
        icon = ConnectorIcons[spec.id] ?: Icons.Outlined.Extension,
        kind = when (spec.kind) {
            ConnectorKind.ONLINE -> Kind.Online
            ConnectorKind.PHONE -> Kind.Local(spec.permissions, spec.access)
        },
        category = spec.category, tools = spec.tools
    )
}

@Composable
fun ConnectorsPage(
    enabledTools: Set<DeviceTool>,
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
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var disconnecting by remember { mutableStateOf<Connector?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    fun accessGranted(kind: Kind.Local): Boolean = localAccessGranted(context, kind, resumeTick)

    fun isConnected(c: Connector): Boolean = when (val kind = c.kind) {
        // Added means its tools are on; a revoked permission leaves it listed so it can be repaired.
        is Kind.Local -> c.tools.any { it in enabledTools }
        is Kind.Online -> c.tools.any { it in enabledTools }
    }

    // Evaluated once per change of its inputs: accessGranted() queries the system, and the page lists the whole catalog.
    val connectedIds = remember(resumeTick, enabledTools) {
        Catalog.filter { isConnected(it) }.map { it.id }.toSet()
    }
    fun connected(c: Connector): Boolean = c.id in connectedIds

    /** An added connector whose Android access was taken away: shown with Repair instead of Connected. */
    val reconnectIds = remember(connectedIds, resumeTick) {
        Catalog.filter { c ->
            c.id in connectedIds && when (val kind = c.kind) {
                is Kind.Local -> !accessGranted(kind)
                is Kind.Online -> false
            }
        }.map { it.id }.toSet()
    }
    fun needsReconnect(c: Connector): Boolean = c.id in reconnectIds

    fun connect(c: Connector) {
        when (val kind = c.kind) {
            is Kind.Online -> onToolsEnabled(c.tools, true, c.name)
            is Kind.Local -> {
                onToolsEnabled(c.tools, true, c.name)
                if (!accessGranted(kind)) {
                    val settingsAction = when (kind.access) {
                        Access.NOTIFICATION_ACCESS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        Access.ACCESSIBILITY -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                        Access.USAGE_ACCESS -> Settings.ACTION_USAGE_ACCESS_SETTINGS
                        Access.ALL_FILES -> if (Build.VERSION.SDK_INT >= 30) Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION else null
                        Access.NONE -> null
                    }
                    if (settingsAction != null) {
                        val intent = Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (kind.access == Access.ALL_FILES) intent.data = android.net.Uri.parse("package:" + context.packageName)
                        context.startActivity(intent)
                    } else {
                        val missing = kind.permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isNotEmpty()) onRequestPermissions(missing)
                    }
                }
            }
        }
    }

    // Every connector, connected or not, grouped by category and A-Z inside each group.
    val shown = Catalog
        .filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
        .sortedBy { it.name.lowercase() }

    val detail = detailId?.let { id -> Catalog.firstOrNull { it.id == id } }
    BackHandler(enabled = detail != null) { detailId = null }

    fun toolLines(c: Connector): List<ToolLine> = c.tools.map { ToolLine(it.label, it.hint, false) }

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
                else -> c.subtitle
            },
            connectLabel = "Connect",
            onRemove = { disconnecting = c },
            connectedLabel = "Connected",
            onConnect = { connect(c) },
            onDisconnect = { disconnecting = c },
            onOpen = { detailId = c.id }
        )
    }

    if (detail != null) {
        val broken = needsReconnect(detail)
        ConnectorDetail(
            connector = detail,
            info = ConnectorRegistry.info(detail.id),
            connected = connected(detail) && !broken,
            reconnect = broken,
            subtitle = when {
                broken && detail.kind is Kind.Local -> "Permission needed"
                else -> detail.subtitle
            },
            tools = toolLines(detail),
            connectLabel = "Connect",
            onConnect = { connect(detail) },
            onDisconnect = { disconnecting = detail },
            onBack = { detailId = null },
            modifier = modifier
        )
    } else Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(4.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search connectors", color = AssistantTextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AssistantTextMuted) },
            singleLine = true,
            shape = CardShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AssistantFieldBackground,
                unfocusedContainerColor = AssistantFieldBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AssistantAccent,
                focusedTextColor = AssistantTextPrimary,
                unfocusedTextColor = AssistantTextPrimary
            ),
            modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, AssistantOutline), CardShape)
        )
        Spacer(Modifier.height(10.dp))
        CategoryChips(
            categories = ConnectorCategory.entries.filter { c -> Catalog.any { it.category == c } }.map { it.title },
            selected = categoryFilter,
            onSelect = { categoryFilter = it }
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (shown.isEmpty()) {
                Text("No connectors match your search.", color = AssistantTextMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
            ConnectorCategory.entries.forEach { category ->
                val inGroup = shown.filter { it.category == category }
                if (inGroup.isNotEmpty() && (categoryFilter == null || categoryFilter == category.title)) {
                    SectionHeading(category.title)
                    // Only the risky group keeps its line, because it is a safety warning; the rest are self-explanatory.
                    if (category.advanced) {
                        Text(category.summary, color = AssistantError,
                            fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    }
                    inGroup.forEach { Card(it) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    disconnecting?.let { c ->
        AlertDialog(
            onDismissRequest = { disconnecting = null },
            title = { Text("Disconnect ${c.name}?") },
            text = {
                Text(
                    when (c.kind) {
                        is Kind.Online -> "The assistant stops using it."
                        is Kind.Local -> "The assistant stops using it. Android permissions stay until you remove them in Settings."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (c.kind) {
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
    Access.NOTIFICATION_ACCESS -> AccessChecks.notificationListener(context) != AccessState.OFF
    Access.ACCESSIBILITY -> AccessChecks.accessibility(context) != AccessState.OFF
    Access.USAGE_ACCESS -> AccessChecks.usageAccess(context)
    Access.ALL_FILES -> AccessChecks.allFiles(context)
    // Approximate location is enough: either of the two location permissions counts. Every other one is required.
    Access.NONE -> {
        val locations = setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val (loc, others) = kind.permissions.partition { it in locations }
        others.all { granted(it) } && (loc.isEmpty() || loc.any { granted(it) })
    }
}

@Composable
private fun SectionHeading(text: String) {
    PageLabel(text, top = 6.dp, bottom = 10.dp)
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
            NeutralIcon(connector.icon)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(connector.name, color = AssistantTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = AssistantTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(8.dp))
            when {
                reconnect -> Pill(if (connector.kind is Kind.Local) "Repair" else "Reconnect", filled = true, onClick = onConnect)
                connected -> StatusBadge(connectedLabel, onClick = onDisconnect)
                else -> Pill(connectLabel, filled = true, onClick = onConnect)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(connector.description, color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        if (reconnect) {
            TextButton(onClick = onRemove) { Text("Remove", color = AssistantTextSecondary, fontSize = 13.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Text("View details", color = AssistantTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AssistantTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** One tool line on a connector's detail page. */
private class ToolLine(val title: String, val text: String, val changesData: Boolean)

/** A connector's own page: what it does, where it runs, what it needs, where data goes and which tools it has. */
@Composable
private fun ConnectorDetail(
    connector: Connector,
    info: ConnectorInfo?,
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
                    Icon(connector.icon, contentDescription = null, tint = AssistantTextPrimary, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(connector.name, color = AssistantTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)
                    Text(subtitle, color = AssistantTextMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                when {
                    reconnect -> Pill(if (connector.kind is Kind.Local) "Repair" else "Reconnect", filled = true, onClick = onConnect)
                    connected -> Pill("Disconnect", filled = false, onClick = onDisconnect)
                    else -> Pill(connectLabel, filled = true, onClick = onConnect)
                }
            }
            DetailSection("About", info?.about ?: connector.description)
            DetailSection("Runs on", info?.runsOn ?: "Your phone")
            info?.let {
                DetailSection("What it needs", it.needs)
                DetailSection("Your data", it.privacy)
            }
            SectionHeading("Tools" + if (tools.isNotEmpty()) " (${tools.size})" else "")
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
                if (filled) Modifier.background(com.assistant.app.ui.theme.AssistantButtonFill, shape)
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
                filled -> com.assistant.app.ui.theme.AssistantOnButtonFill
                enabled -> AssistantTextSecondary
                else -> AssistantTextMuted
            },
            fontSize = if (check) 14.sp else 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Small green-dot "Connected" badge: tells the state without crowding the name. Tapping it offers to disconnect. */
@Composable
private fun StatusBadge(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(30.dp).clip(shape).background(AssistantFieldBackground, shape)
            .border(BorderStroke(1.dp, AssistantOutline), shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFF22C55E)))
        Spacer(Modifier.size(6.dp))
        Text(text, color = AssistantTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
