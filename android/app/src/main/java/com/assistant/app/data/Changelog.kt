package com.assistant.app.data

/** One block of a version's notes, for example "Added" with its bullet points. */
data class ChangelogSection(val title: String, val items: List<String>)

/** What changed in one released version. [date] is empty when the changelog gives none. */
data class ChangelogEntry(val version: String, val date: String, val sections: List<ChangelogSection>)

/**
 * Reads the markdown changelog shipped with the app (assets/changelog.md). Each "## 1.2.3 - 2026-01-31" heading starts
 * a version, each "### Added" heading a section and each "- " line a point. Anything before the first version heading
 * is ignored. The same file decides the app's version number and the notes sent with an update.
 */
object Changelog {
    private val VERSION_HEADING = Regex("""^##\s+v?(\d+\.\d+\.\d+)\s*(?:[-–—]\s*(.+))?$""")
    private val SECTION_HEADING = Regex("""^###\s+(.+)$""")

    fun parse(text: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var date = ""
        var sections = mutableListOf<ChangelogSection>()
        var sectionTitle: String? = null
        var items = mutableListOf<String>()

        fun closeSection() {
            sectionTitle?.let { if (items.isNotEmpty()) sections.add(ChangelogSection(it, items.toList())) }
            sectionTitle = null
            items = mutableListOf()
        }

        fun closeVersion() {
            closeSection()
            version?.let { entries.add(ChangelogEntry(it, date, sections.toList())) }
            version = null
            date = ""
            sections = mutableListOf()
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val versionMatch = VERSION_HEADING.matchEntire(line)
            if (versionMatch != null) {
                closeVersion()
                version = versionMatch.groupValues[1]
                date = versionMatch.groupValues[2].trim()
                continue
            }
            if (version == null) continue
            val sectionMatch = SECTION_HEADING.matchEntire(line)
            if (sectionMatch != null) {
                closeSection()
                sectionTitle = sectionMatch.groupValues[1].trim()
            } else if (line.startsWith("- ") && sectionTitle != null) {
                items.add(line.removePrefix("- ").trim())
            }
        }
        closeVersion()
        return entries
    }
}
