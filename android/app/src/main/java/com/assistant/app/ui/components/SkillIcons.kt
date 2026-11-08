package com.assistant.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** Words that point at an icon, most specific first. The first row with a hit wins. */
private val SkillIconWords: List<Pair<ImageVector, List<String>>> = listOf(
    Icons.Outlined.Chat to listOf("whatsapp", "messag", "sms", "chat", "telegram"),
    Icons.Outlined.Call to listOf("call", "phone", "dial", "contact"),
    Icons.Outlined.Mail to listOf("email", "e-mail", "gmail", "mail", "inbox"),
    Icons.Outlined.PlayCircle to listOf("youtube", "play", "stream"),
    Icons.Outlined.Videocam to listOf("video", "movie", "film", "camera"),
    Icons.Outlined.MusicNote to listOf("music", "song", "spotify", "playlist", "radio"),
    Icons.Outlined.Photo to listOf("photo", "picture", "image", "gallery", "wallpaper"),
    Icons.Outlined.Map to listOf("map", "navigat", "direction", "route", "location", "place"),
    Icons.Outlined.Flight to listOf("trip", "travel", "flight", "holiday", "hotel"),
    Icons.Outlined.Restaurant to listOf("recipe", "cook", "food", "meal", "restaurant", "diet"),
    Icons.Outlined.FitnessCenter to listOf("fitness", "workout", "exercise", "gym", "steps"),
    Icons.Outlined.FavoriteBorder to listOf("health", "medic", "sleep", "wellness"),
    Icons.Outlined.Event to listOf("calendar", "meeting", "appointment", "event", "birthday"),
    Icons.Outlined.Alarm to listOf("alarm", "reminder", "timer", "remind"),
    Icons.Outlined.EditNote to listOf("note", "journal", "diary", "todo", "to do", "list"),
    Icons.Outlined.ShoppingCart to listOf("shop", "buy", "order", "cart", "grocer"),
    Icons.Outlined.Payments to listOf("money", "budget", "expense", "bill", "bank", "pay", "finance"),
    Icons.Outlined.WbSunny to listOf("weather", "forecast", "temperature"),
    Icons.Outlined.Newspaper to listOf("news", "headline", "article"),
    Icons.Outlined.School to listOf("study", "learn", "teach", "school", "quiz", "exam", "language"),
    Icons.Outlined.Translate to listOf("translat"),
    Icons.Outlined.Code to listOf("code", "program", "develop", "script", "debug"),
    Icons.Outlined.SportsEsports to listOf("game", "puzzle", "trivia"),
    Icons.Outlined.Search to listOf("search", "research", "lookup", "find", "web"),
    Icons.Outlined.Description to listOf("document", "pdf", "report", "write", "essay", "letter"),
    Icons.Outlined.Apps to listOf("ultron", "feature", "ability", "abilities")
)

/**
 * An icon that matches what a skill is about. The name is checked first, then the category, then the description, so a
 * skill called "trip-planner" gets a plane even when its description mentions messages. Anything unmatched gets the sparkle.
 */
fun skillIcon(name: String, category: String, description: String): ImageVector {
    for (field in listOf(name, category, description)) {
        val text = field.lowercase().replace('-', ' ').replace('_', ' ')
        SkillIconWords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.let { return it.first }
    }
    return Icons.Outlined.AutoAwesome
}
