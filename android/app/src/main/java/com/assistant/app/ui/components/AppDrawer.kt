package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import com.assistant.app.ui.theme.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.BuildConfig
import com.assistant.app.data.AssistantOptions
import com.assistant.app.data.ChatSession
import com.assistant.app.data.AssistantSettings
import com.assistant.app.data.MicSensitivity
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantAccentFill
import com.assistant.app.ui.theme.AssistantAccentAlt
import com.assistant.app.ui.theme.AssistantError
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.ThemeMode
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** Pages reachable from the left menu. */
enum class NavTab(val label: String, val icon: ImageVector) {
    HOME("Assistant", AssistantIcons.Home),
    SEARCH("Search chats", Icons.Outlined.Search),
    LIBRARY("Library", Icons.Outlined.PhotoLibrary),
    KNOWLEDGE("Knowledge", AssistantIcons.Knowledge),
    MEMORY("Memory", Icons.Outlined.Psychology),
    CONNECTORS("Connectors", Icons.Outlined.Extension),
    PERSONALIZATION("Personalization", Icons.Outlined.AutoAwesome),
    SETTINGS("Settings", AssistantIcons.Settings),
    USAGE("Usage", Icons.Outlined.BarChart),
    STORAGE("Storage", Icons.Outlined.Storage),
    DEVELOPER("Developer", Icons.Outlined.Code),
    ABOUT("About", Icons.Outlined.Info)
}

/** Height of the slim bar at the top of every page; pages reserve this much space under it. */
val AppTopBarHeight = 56.dp

private val RowHeight = 44.dp

@Composable
fun AppDrawerContent(
    selected: NavTab,
    assistantName: String,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    chats: List<ChatSession> = emptyList(),
    activeChatId: String? = null,
    onOpenChat: (String) -> Unit = {},
    onDeleteChat: (String) -> Unit = {}
) {
    var chatToDelete by remember { mutableStateOf<ChatSession?>(null) }
    chatToDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("\"${chat.title}\" will be removed for good.") },
            confirmButton = {
                TextButton(onClick = { onDeleteChat(chat.id); chatToDelete = null }) { Text("Delete", color = AssistantError) }
            },
            dismissButton = { TextButton(onClick = { chatToDelete = null }) { Text("Cancel") } }
        )
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 18.dp)) {
        DrawerHeader(assistantName)
        Spacer(Modifier.height(20.dp))
        NavTab.entries.forEach { tab ->
            // Small captions group the working pages, the preferences and the app info.
            when (tab) {
                NavTab.HOME -> return@forEach // Home has no row: back from any page returns to it.
                NavTab.LIBRARY -> NavSection("Workspace")
                NavTab.PERSONALIZATION -> NavSection("Preferences")
                NavTab.DEVELOPER -> NavSection("App")
                else -> Unit
            }
            NavRow(tab, tab == selected) { onSelect(tab) }
        }
        if (chats.isNotEmpty()) {
            NavSection("Chats")
            chats.take(MAX_DRAWER_CHATS).forEach { chat ->
                ChatRow(chat.title, chat.id == activeChatId && selected == NavTab.HOME,
                    onClick = { onOpenChat(chat.id) }, onLongClick = { chatToDelete = chat })
            }
        }
    }
}

private const val MAX_DRAWER_CHATS = 30

/** One saved chat in the menu: just its title, highlighted while it is the open chat; long-press deletes it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(title: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        title,
        color = if (selected) AssistantAccent else AssistantTextSecondary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
            .then(if (selected) Modifier.background(AssistantSelectedBg, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 12.dp, vertical = 11.dp)
    )
}

@Composable
private fun NavSection(title: String, first: Boolean = false) {
    Text(
        title.uppercase(),
        color = AssistantTextMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 12.dp, top = if (first) 0.dp else 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun NavRow(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.5.dp).height(RowHeight).clip(shape)
            .then(
                if (selected) Modifier.background(AssistantSelectedBg, shape)
                    .border(BorderStroke(1.dp, AssistantAccent.copy(alpha = 0.35f)), shape)
                else Modifier
            )
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = if (selected) AssistantAccent else AssistantTextMuted)
        Spacer(Modifier.size(14.dp))
        Text(
            tab.label,
            color = if (selected) AssistantAccent else AssistantTextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DrawerHeader(assistantName: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconTile(44.dp)
        Spacer(Modifier.size(14.dp))
        Text(
            assistantName,
            color = AssistantTextPrimary,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Slim bar above every page: menu button (hidden when the menu is always visible) and the page title. */
@Composable
fun AppTopBar(
    title: String,
    showMenuButton: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = AppTopBarHeight,
    /** When set, a "new chat" button is shown at the right end of the bar. */
    onNewChat: (() -> Unit)? = null,
    /** When set, a sun/moon button at the right end switches between light and dark. */
    onToggleTheme: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMenuButton) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(AssistantSurfaceElevated)
                    .border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(13.dp))
                    .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = AssistantTextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
        } else {
            Spacer(Modifier.size(12.dp))
        }
        Text(
            title,
            color = AssistantTextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onNewChat != null) {
            IconButton(onClick = onNewChat) {
                Icon(Icons.Filled.AddComment, contentDescription = "New chat", tint = AssistantTextPrimary)
            }
        }
        if (onToggleTheme != null) {
            val dark = com.assistant.app.ui.theme.ActivePalette.current.dark
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode",
                    tint = AssistantTextPrimary
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** Drawer surface colour, shared by the modal and permanent variants. */
val AppDrawerContainerColor: Color get() = AssistantSurface
