package com.assistant.app.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import io.objectbox.BoxStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** A picture the user added, as the app lists it. */
data class StoredImage(
    val id: Long,
    val title: String,
    val description: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdAtMillis: Long
)

sealed interface AddImageResult {
    data class Added(val image: StoredImage) : AddImageResult
    /** The same picture is already in the library under [existing]. */
    data class Duplicate(val existing: StoredImage) : AddImageResult
    object NotAnImage : AddImageResult
    object LibraryFull : AddImageResult
}

/** Ranks pictures against what the user said, forgiving partial words and typos. Pure, so it is easy to test. */
object ImageSearch {
    fun rank(images: List<StoredImage>, query: String?, now: Long = System.currentTimeMillis()): List<StoredImage> {
        val request = FileSearch.parse(query, now)
        val byKey = images.associateBy { it.id }
        val candidates = images.map { FileCandidate("${it.title} ${it.description}", "", it.createdAtMillis, it.id) }
        return FileSearch.rank(candidates, request, hasExtension = false).mapNotNull { byKey[it.file.key] }
    }
}

/**
 * The pictures the user wants the assistant to be able to show. Each one is shrunk to a JPEG file in the app's private
 * folder; the title and description are what the assistant searches. Nothing leaves the phone.
 */
class ImageLibrary(store: BoxStore, private val dir: File) {
    private val box = store.boxFor(ImageEntity::class.java)

    companion object {
        const val MAX_TITLE = 60
        const val MAX_DESCRIPTION = 400
        const val MAX_IMAGES = 200
        const val MAX_EDGE = 1600
        private const val JPEG_QUALITY = 85

        @Volatile private var shared: ImageLibrary? = null

        /** Where a picture's JPEG lives, given the app's files folder. */
        fun fileIn(filesDir: File, image: StoredImage) = File(File(filesDir, "images"), image.fileName)

        /** One library per process, so the pages and the voice tools see the same pictures. */
        fun forApp(app: Application): ImageLibrary = shared ?: synchronized(this) {
            shared ?: ImageLibrary(RagDocumentStore.sharedObjectBoxStore(app), File(app.filesDir, "images")).also { shared = it }
        }

        /** Smaller-than-limit pictures are kept as they are; bigger ones are scaled so the longest side is [MAX_EDGE]. */
        fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= MAX_EDGE) return width to height
            val scale = MAX_EDGE.toFloat() / longest
            return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
        }

        /** Decodes, fixes the camera rotation, shrinks and re-encodes as JPEG. Null when the bytes are not a picture. */
        fun shrink(bytes: ByteArray): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val rotation = try {
                when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                0f
            }
            val (w, h) = scaledSize(decoded.width, decoded.height)
            var bitmap = if (w != decoded.width || h != decoded.height) Bitmap.createScaledBitmap(decoded, w, h, true) else decoded
            if (rotation != 0f) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        }

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun add(rawBytes: ByteArray, title: String, description: String, nowMillis: Long = System.currentTimeMillis()): AddImageResult {
        if (box.count() >= MAX_IMAGES) return AddImageResult.LibraryFull
        val jpeg = shrink(rawBytes) ?: return AddImageResult.NotAnImage
        val hash = sha256(jpeg)
        box.query(ImageEntity_.sha256.equal(hash)).build().use { q -> q.findFirst() }?.let { return AddImageResult.Duplicate(it.toStored()) }
        dir.mkdirs()
        val entity = ImageEntity(
            title = clean(title, MAX_TITLE).ifBlank { "Picture" }, description = clean(description, MAX_DESCRIPTION),
            sha256 = hash, sizeBytes = jpeg.size.toLong(), createdAtMillis = nowMillis
        )
        val id = box.put(entity)
        entity.fileName = "$id.jpg"
        File(dir, entity.fileName).writeBytes(jpeg)
        box.put(entity)
        return AddImageResult.Added(entity.toStored())
    }

    fun update(id: Long, title: String, description: String): Boolean {
        val entity = box.get(id) ?: return false
        entity.title = clean(title, MAX_TITLE).ifBlank { entity.title }
        entity.description = clean(description, MAX_DESCRIPTION)
        box.put(entity)
        return true
    }

    fun delete(id: Long): Boolean {
        val entity = box.get(id) ?: return false
        File(dir, entity.fileName).delete()
        return box.remove(id)
    }

    fun clearAll() {
        box.removeAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Newest first. */
    fun list(): List<StoredImage> = box.all.sortedByDescending { it.createdAtMillis }.map { it.toStored() }

    fun count(): Int = box.count().toInt()

    fun totalBytes(): Long = box.all.sumOf { it.sizeBytes }

    fun fileOf(image: StoredImage): File = File(dir, image.fileName)

    fun search(query: String?): List<StoredImage> = ImageSearch.rank(list(), query)

    private fun clean(text: String, max: Int) = text.trim().replace(Regex("\\s+"), " ").take(max)

    private fun ImageEntity.toStored() = StoredImage(id, title, description, fileName, sizeBytes, createdAtMillis)
}
