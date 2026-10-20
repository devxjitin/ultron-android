package com.assistant.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lets the voice assistant press Back/Home/Recents, scroll, and (when the matching tool is switched
 * on in Settings) read the visible screen, tap, and type. It only acts when a tool asks it to and
 * subscribes to no screen content by itself. Password fields are never read or typed into.
 */
class AssistantAccessibilityService : AccessibilityService() {

    /** One visible element from the last [readScreen], so the model can refer to it by number. */
    data class ScreenItem(val label: String, val role: String, val tappable: Boolean, val bounds: Rect)

    @Volatile private var lastItems: List<ScreenItem> = emptyList()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---------------------------------------------------------------- reading

    /** Package name of the app currently on screen, or null when nothing is readable. */
    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /** A numbered list of what is visible, or null when the screen cannot be read. */
    fun readScreen(): String? {
        val root = rootInActiveWindow ?: return null
        val items = mutableListOf<ScreenItem>()
        collect(root, inheritedTappable = false, depth = 0, out = items)
        lastItems = items
        val builder = StringBuilder()
        items.forEachIndexed { index, item ->
            val line = "[${index + 1}] ${item.role} \"${item.label}\"" +
                (if (item.tappable) " (tappable)" else "") + " @${item.bounds.centerX()},${item.bounds.centerY()}\n"
            if (builder.length + line.length > MAX_CHARS) return@forEachIndexed
            builder.append(line)
        }
        return builder.toString()
    }

    /** True when [text] appears (case-insensitive) in the current screen's text. */
    fun screenContains(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return !root.findAccessibilityNodeInfosByText(text).isNullOrEmpty()
    }

    private fun collect(node: AccessibilityNodeInfo, inheritedTappable: Boolean, depth: Int, out: MutableList<ScreenItem>) {
        if (depth > MAX_DEPTH || out.size >= MAX_ITEMS) return
        val tappable = inheritedTappable || node.isClickable
        if (node.isVisibleToUser) {
            val label = when {
                node.isPassword -> "(password field)"
                else -> (node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.hintText?.toString()?.takeIf { it.isNotBlank() })
            }
            if (label != null) {
                val bounds = Rect().also(node::getBoundsInScreen)
                if (!bounds.isEmpty) out.add(ScreenItem(label.take(MAX_LABEL), roleOf(node), tappable, bounds))
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, tappable, depth + 1, out)
        }
    }

    private fun roleOf(node: AccessibilityNodeInfo): String {
        val name = node.className?.toString().orEmpty()
        return when {
            node.isEditable || name.endsWith("EditText") -> "input"
            name.endsWith("Button") -> "button"
            name.endsWith("CheckBox") || name.endsWith("Switch") || name.endsWith("RadioButton") -> "toggle"
            name.endsWith("ImageView") || name.endsWith("ImageButton") -> "image"
            name.endsWith("TextView") -> "text"
            else -> "view"
        }
    }

    // ---------------------------------------------------------------- acting

    /** Clicks the [occurrence]-th (1-based) element whose text or description contains [text]. */
    suspend fun clickByText(text: String, occurrence: Int): String {
        val root = rootInActiveWindow ?: return "status=unavailable\nThe screen cannot be read right now."
        val matches = root.findAccessibilityNodeInfosByText(text).orEmpty()
        val node = matches.getOrNull(occurrence - 1)
            ?: return "status=not_found\nNothing on screen matches \"$text\". Call read_screen to see what is there."
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "status=ok\nClicked \"$text\"."
        }
        val bounds = Rect().also(node::getBoundsInScreen)
        return if (!bounds.isEmpty && tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())) "status=ok\nTapped \"$text\"."
        else "status=failed\nCould not click \"$text\"."
    }

    /** Taps the element with this number from the latest [readScreen]. */
    suspend fun clickItem(number: Int): String {
        val item = lastItems.getOrNull(number - 1)
            ?: return "status=invalid\nNo item $number. Call read_screen first and use a number from it."
        return if (tap(item.bounds.centerX().toFloat(), item.bounds.centerY().toFloat())) "status=ok\nTapped item $number (${item.label})."
        else "status=failed\nThe tap did not go through."
    }

    /** A short finger tap at screen coordinates. */
    suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        return withTimeoutOrNull(3_000L) {
            suspendCancellableCoroutine { continuation ->
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (continuation.isActive) continuation.resume(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { if (continuation.isActive) continuation.resume(false) }
                }, null)
                if (!accepted && continuation.isActive) continuation.resume(false)
            }
        } == true
    }

    /** Types into the focused text field (or the first one on screen). Never touches password fields. */
    fun typeText(text: String, append: Boolean): String {
        val root = rootInActiveWindow ?: return "status=unavailable\nThe screen cannot be read right now."
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: firstEditable(root, 0)
            ?: return "status=not_found\nNo text field is on screen. Tap a text field first."
        if (field.isPassword) return "status=refused\nThat is a password field; the assistant does not type into those."
        val existing = if (append && !field.isShowingHintText) field.text?.toString().orEmpty() else ""
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        return if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) "status=ok\nTyped the text."
        else "status=failed\nThe field did not accept the text."
    }

    /** Presses Enter/Search/Go on the focused field (Android 11 or newer). */
    fun pressEnter(): String {
        if (Build.VERSION.SDK_INT < 30) return "status=unavailable\nThis needs Android 11 or newer."
        val field = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "status=not_found\nNo text field is focused."
        return if (field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) "status=ok\nPressed Enter."
        else "status=failed\nThe field did not accept Enter."
    }

    private fun firstEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { firstEditable(it, depth + 1) }
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null

        private const val MAX_DEPTH = 30
        private const val MAX_ITEMS = 80
        private const val MAX_LABEL = 80
        private const val MAX_CHARS = 4_000
    }
}
