package com.assistant.app.data

import com.assistant.app.BuildConfig

/**
 * A Google Workspace service reachable through Google's own MCP server. Each one is added on its own,
 * with its own Google sign-in (any account), and asks only for its own permission.
 */
enum class GoogleService(
    val id: String,
    val label: String,
    /** Last part of the server URL, picks this service on our Google Workspace MCP server. */
    val path: String,
    val scope: String,
    /** Prefix that routes a tool call to this service and keeps names unique. */
    val toolPrefix: String
) {
    CALENDAR("google_calendar", "Google Calendar", "calendar", "https://www.googleapis.com/auth/calendar", "gcal_"),
    GMAIL("gmail", "Gmail", "gmail", "https://www.googleapis.com/auth/gmail.modify", "gmail_"),
    DRIVE("google_drive", "Google Drive", "drive", "https://www.googleapis.com/auth/drive", "gdrive_"),
    DOCS("google_docs", "Google Docs", "docs", "https://www.googleapis.com/auth/documents", "gdocs_"),
    SHEETS("google_sheets", "Google Sheets", "sheets", "https://www.googleapis.com/auth/spreadsheets", "gsheets_"),
    SLIDES("google_slides", "Google Slides", "slides", "https://www.googleapis.com/auth/presentations", "gslides_");

    /** Our own MCP server (built on Google's REST APIs); the app's Google token is sent along as the Bearer token. */
    val url: String get() = BuildConfig.GOOGLE_MCP_URL.trimEnd('/') + "/" + path

    companion object {
        fun fromToolName(name: String): GoogleService? = entries.firstOrNull { name.startsWith(it.toolPrefix) }
    }
}

/** Every enabled tool of every added Google service, each paired with the service that owns it. */
fun AssistantSettings.enabledGoogleTools(): List<Pair<GoogleService, McpToolSpec>> =
    GoogleService.entries.filter { it.id in googleAccounts }.flatMap { service ->
        val on = enabledGoogleTools[service.id].orEmpty()
        googleTools[service.id].orEmpty().filter { it.name in on }.map { service to it }
    }
