package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurface
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** Pages reachable from the left menu. */
enum class NavTab(val label: String, val icon: ImageVector) {
    HOME("Home", AssistantIcons.Home),
    KNOWLEDGE("Knowledge", AssistantIcons.Knowledge),
    MEMORY("Memory", Icons.Outlined.Psychology),
    HISTORY("History", Icons.Outlined.History),
    SKILLS("Skills", Icons.Outlined.Bolt),
    CONNECTORS("Connectors", Icons.Outlined.Extension),
    SETTINGS("Settings", AssistantIcons.Settings),
    USAGE("Usage", Icons.Outlined.Insights),
    STORAGE("Storage", Icons.Outlined.Inventory2),
    DEVELOPER("Developer", Icons.Outlined.Code),
    ABOUT("About", Icons.Outlined.Info)
}

/** Height of the slim bar at the top of every page; pages reserve this much space under it. */
val AppTopBarHeight = 56.dp

private val RowHeight = 52.dp

@Composable
fun AppDrawerContent(
    selected: NavTab,
    assistantName: String,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        DrawerHeader(assistantName, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(10.dp))
            NavTab.entries.filter { it != NavTab.SETTINGS }.forEach { tab ->
                // Plain captions group the working pages, the numbers and the app info.
                when (tab) {
                    NavTab.USAGE -> NavSection("Insights")
                    NavTab.DEVELOPER -> NavSection("App")
                    else -> Unit
                }
                NavRow(tab, tab == selected) { onSelect(tab) }
            }
            Spacer(Modifier.height(16.dp))
        }
        DrawerFooter(assistantName, selected == NavTab.SETTINGS) { onSelect(NavTab.SETTINGS) }
    }
}

@Composable
private fun NavSection(title: String) {
    Text(
        text = title.uppercase(),
        color = AssistantTextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun NavRow(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clip(shape)
            .then(if (selected) Modifier.background(AssistantSelectedBg, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (selected) AssistantAccent else AssistantTextSecondary)
        Spacer(Modifier.size(22.dp))
        Text(
            tab.label,
            color = if (selected) AssistantAccent else AssistantTextPrimary,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Large page title on the left and a close button on the right, like the rest of the menu: flat, no borders. */
@Composable
private fun DrawerHeader(assistantName: String, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(72.dp).padding(start = 28.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            assistantName,
            color = AssistantTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close menu", tint = AssistantTextPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Pinned at the bottom: the app icon and name on the left, a settings button on the right. */
@Composable
private fun DrawerFooter(assistantName: String, settingsSelected: Boolean, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconTile(52.dp)
        Spacer(Modifier.size(16.dp))
        Text(
            assistantName,
            color = AssistantTextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onSettings,
            modifier = Modifier.then(if (settingsSelected) Modifier.background(AssistantSurfaceElevated, CircleShape) else Modifier)
        ) {
            Icon(NavTab.SETTINGS.icon, contentDescription = "Settings", tint = AssistantTextSecondary, modifier = Modifier.size(26.dp))
        }
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
    /** When set, a day/night button is shown at the right end of the bar. */
    onToggleTheme: (() -> Unit)? = null,
    isDarkTheme: Boolean = false
) {
    Row(
        modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showMenuButton) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = AssistantTextPrimary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.size(4.dp))
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
        if (onToggleTheme != null) {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to day theme" else "Switch to night theme",
                    tint = AssistantTextPrimary
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}

/** Drawer surface colour, shared by the modal and permanent variants. */
val AppDrawerContainerColor: Color get() = AssistantSurface
