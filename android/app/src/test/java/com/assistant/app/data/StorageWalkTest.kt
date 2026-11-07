package com.assistant.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageWalkTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(root: File, path: String, age: Long = 0): File =
        File(root, path).also { it.parentFile.mkdirs(); it.writeText("x"); it.setLastModified(1_000_000_000_000L - age) }

    @Test fun `folder requests resolve inside the storage root`() {
        val root = tmp.newFolder("root")
        File(root, "Download/Bills").mkdirs()
        assertEquals(File(root, "Download/Bills").canonicalFile, StorageWalk.resolve(root, "Download/Bills"))
        assertEquals(File(root, "Download").canonicalFile, StorageWalk.resolve(root, "/storage/emulated/0/Download"))
        assertEquals(File(root, "Download").canonicalFile, StorageWalk.resolve(root, "Internal storage/Download"))
        assertEquals(root.canonicalFile, StorageWalk.resolve(root, ""))
        assertEquals(root.canonicalFile, StorageWalk.resolve(root, null))
    }

    @Test fun `a request cannot climb out of the storage root`() {
        val root = tmp.newFolder("root")
        assertNull(StorageWalk.resolve(root, "../outside"))
        assertNull(StorageWalk.resolve(root, "Download/../../outside"))
    }

    @Test fun `walk lists files newest first and skips hidden and private folders`() {
        val root = tmp.newFolder("root")
        file(root, "Download/old.pdf", age = 5000)
        file(root, "DCIM/new.jpg", age = 0)
        file(root, ".trash/secret.txt")
        file(root, "Android/data/com.other/cache.bin")
        file(root, "Android/media/ok.txt", age = 100)
        val names = StorageWalk.walk(root, 100).map { it.name }
        assertEquals(listOf("new.jpg", "ok.txt", "old.pdf"), names)
    }

    @Test fun `walk stops at the limit`() {
        val root = tmp.newFolder("root")
        repeat(10) { file(root, "a/f$it.txt") }
        assertEquals(4, StorageWalk.walk(root, 4).size)
    }

    @Test fun `folders finds nested folders but not hidden ones`() {
        val root = tmp.newFolder("root")
        File(root, "Documents/Bills 2025").mkdirs()
        File(root, ".hidden").mkdirs()
        val names = StorageWalk.folders(root, 100).map { it.name }
        assertTrue("Bills 2025" in names)
        assertTrue(".hidden" !in names)
    }
}
