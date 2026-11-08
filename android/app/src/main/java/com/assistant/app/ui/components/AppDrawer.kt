package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
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
    PROFILE("Profile", Icons.Outlined.AccountCircle),
    KNOWLEDGE("Knowledge", AssistantIcons.Knowledge),
    MEMORY("Memory", Icons.Outlined.Psychology),
    HISTORY("History", Icons.Outlined.History),
    IMAGES("Images", Icons.Outlined.Image),
    SKILLS("Skills", Icons.Outlined.Bolt),
    SCHEDULE("Schedule", Icons.Outlined.Schedule),
    CONNECTORS("Connectors", Icons.Outlined.Extension),
    SETTINGS("Settings", AssistantIcons.Settings),
    USAGE("Usage", Icons.Outlined.Insights),
    STORAGE("Storage", Icons.Outlined.Inventory2),
    DOWNLOADS("Downloads", Icons.Outlined.Download),
    DEVELOPER("Developer", Icons.Outlined.Code),
    HELP("Help & support", Icons.AutoMirrored.Outlined.HelpOutline),
    ABOUT("About", Icons.Outlined.Info)
}

/** Height of the slim bar at the top of every page; pages reserve this much space under it. */
val AppTopBarHeight = 56.dp

private val RowHeight = 40.dp

/** The menu in reading order: the basics, what Ultron does, what it knows about you, the numbers, then the app itself. */
private val DrawerGroups: List<Pair<String?, List<NavTab>>> = listOf(
    null to listOf(NavTab.HOME),
    "Assistant" to listOf(NavTab.SKILLS, NavTab.CONNECTORS, NavTab.SCHEDULE),
    "Your data" to listOf(NavTab.KNOWLEDGE, NavTab.MEMORY, NavTab.HISTORY, NavTab.DOWNLOADS),
    "Insights" to listOf(NavTab.USAGE, NavTab.STORAGE),
    "App" to listOf(NavTab.SETTINGS, NavTab.HELP, NavTab.ABOUT)
)

@Composable
fun AppDrawerContent(
    selected: NavTab,
    assistantName: String,
    /** The signed-in buyer's name (or the name set in Settings); blank shows "Your profile". */
    userName: String,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        DrawerHeader(assistantName, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(4.dp))
            // A hairline and a plain caption start each group. Settings sits in the footer.
            DrawerGroups.forEach { (title, tabs) ->
                if (title != null) NavSection(title)
                tabs.forEach { tab -> NavRow(tab, tab == selected) { onSelect(tab) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        DrawerFooter(userName, selected == NavTab.PROFILE) { onSelect(NavTab.PROFILE) }
    }
}

@Composable
private fun NavSection(title: String) {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(AssistantOutline))
    Text(
        text = title,
        color = AssistantTextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun NavRow(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clip(shape)
            .then(if (selected) Modifier.background(AssistantSelectedBg, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) AssistantTextPrimary else AssistantTextSecondary)
        Spacer(Modifier.size(16.dp))
        Text(
            tab.label,
            color = AssistantTextPrimary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The assistant's name in a serif on the left and a thin close button on the right: flat, no borders. */
@Composable
private fun DrawerHeader(assistantName: String, onClose: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            assistantName,
            color = AssistantTextPrimary,
            fontFamily = com.assistant.app.ui.theme.AssistantSerif,
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close menu", tint = AssistantTextSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Pinned at the bottom: a circle with the first letter of the user's name, then the name. Tapping it opens Profile. */
@Composable
private fun DrawerFooter(userName: String, selected: Boolean, onProfile: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val shown = userName.trim().ifBlank { "Your profile" }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(shape)
            .then(if (selected) Modifier.background(AssistantSelectedBg, shape) else Modifier)
            .clickable(onClick = onProfile).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(AssistantSurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                userName.trim().firstOrNull()?.uppercase() ?: "U",
                color = AssistantTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            shown,
            color = AssistantTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Three thin lines of different length, left aligned: the menu button used on every page. */
@Composable
private fun MenuLinesIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(24.dp)) {
        val stroke = 1.7.dp.toPx()
        val left = 3.dp.toPx()
        val lines = listOf(6.dp.toPx() to 18.dp.toPx(), 12.dp.toPx() to 12.dp.toPx(), 18.dp.toPx() to 7.dp.toPx())
        lines.forEach { (y, length) ->
            drawLine(tint, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(left + length, y),
                strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Slim bar above every page: a thin menu button on the left, the page title in a serif in the middle, the theme button on the right. */
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
    Box(modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp)) {
        Text(
            title,
            color = AssistantTextPrimary,
            fontFamily = com.assistant.app.ui.theme.AssistantSerif,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp)
        )
        if (showMenuButton) {
            IconButton(onClick = onMenuClick, modifier = Modifier.align(Alignment.CenterStart)) {
                MenuLinesIcon(AssistantTextPrimary)
            }
        }
        if (onToggleTheme != null) {
            IconButton(onClick = onToggleTheme, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (isDarkTheme) "Switch to day theme" else "Switch to night theme",
                    tint = AssistantTextPrimary, modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** Drawer surface colour, shared by the modal and permanent variants. */
val AppDrawerContainerColor: Color get() = com.assistant.app.ui.theme.AssistantBackground
