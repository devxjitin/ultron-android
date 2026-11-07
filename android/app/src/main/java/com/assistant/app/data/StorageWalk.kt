package com.assistant.app.data

import java.io.File

/** Plain file-system helpers for the All files connector. No Android classes, so they are easy to test. */
object StorageWalk {
    /** Folders other apps own and Android hides, or that only hold junk for searching. */
    private fun skipped(dir: File, root: File): Boolean {
        if (dir.name.startsWith(".")) return true
        val relative = dir.relativeTo(root).path.replace('\\', '/')
        return relative.equals("Android/data", true) || relative.equals("Android/obb", true)
    }

    /** A request such as "Download/Bills", "/Download" or "Internal storage/Download" as a folder inside [root], or null if it escapes. */
    fun resolve(root: File, input: String?): File? {
        var path = input.orEmpty().trim().replace('\\', '/')
        val prefixes = listOf(root.path.replace('\\', '/'), "storage/emulated/0", "/storage/emulated/0", "sdcard", "/sdcard", "internal storage", "internal shared storage")
        for (prefix in prefixes) {
            if (path.equals(prefix, true)) { path = ""; break }
            if (path.startsWith("$prefix/", true)) { path = path.substring(prefix.length + 1); break }
        }
        path = path.trim('/')
        val target = if (path.isEmpty()) root else File(root, path)
        val canonicalRoot = root.canonicalFile
        val canonical = try { target.canonicalFile } catch (e: Exception) { return null }
        val inside = canonical == canonicalRoot || canonical.path.startsWith(canonicalRoot.path + File.separator)
        return canonical.takeIf { inside }
    }

    /** Up to [limit] files under [root], newest first. Hidden folders and other apps' private folders are skipped. */
    fun walk(root: File, limit: Int): List<File> {
        val found = ArrayList<File>()
        val pending = ArrayDeque<File>().apply { add(root) }
        while (pending.isNotEmpty() && found.size < limit) {
            val dir = pending.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                if (found.size >= limit) break
                if (child.isDirectory) { if (!skipped(child, root)) pending.add(child) }
                else if (!child.name.startsWith(".")) found.add(child)
            }
        }
        return found.sortedByDescending { it.lastModified() }
    }

    /** Every folder under [root] (same skips), for finding a folder by a loose name. */
    fun folders(root: File, limit: Int): List<File> {
        val found = ArrayList<File>()
        val pending = ArrayDeque<File>().apply { add(root) }
        while (pending.isNotEmpty() && found.size < limit) {
            val dir = pending.removeFirst()
            for (child in dir.listFiles().orEmpty()) {
                if (child.isDirectory && !skipped(child, root)) { found.add(child); pending.add(child) }
            }
        }
        return found
    }
}
