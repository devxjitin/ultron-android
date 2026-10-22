package com.assistant.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.assistant.app.data.LinkPreviews
import com.assistant.app.ui.theme.AssistantFieldBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantSelectedBg
import com.assistant.app.ui.theme.AssistantSurfaceElevated
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** The block-level pieces of a Markdown text. */
@androidx.compose.runtime.Immutable
internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Item(val marker: String, val depth: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>, val aligns: List<TextAlign>) : MdBlock
    data class Picture(val alt: String, val url: String) : MdBlock
    data object Rule : MdBlock
}

/**
 * Renders a chat reply written in Markdown: headings, bold/italic/strike, inline and fenced code,
 * lists, quotes, links, rules and tables. Text that is still streaming (an unclosed `**` or code
 * fence) simply shows as it is until the closing part arrives.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = AssistantTextPrimary) {
    // While a reply streams, only the part after the last finished paragraph is parsed again.
    val parser = remember { IncrementalMarkdown() }
    val blocks = remember(text) { parser.update(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlockView(block, color) }
    }
}

/** One block on its own, so a finished block is not drawn again when only the end of the reply changed. */
@Composable
private fun MarkdownBlockView(block: MdBlock, color: Color) {
    when (block) {
        is MdBlock.Paragraph -> Text(inline(block.text), color = color, style = BodyStyle)
        is MdBlock.Heading -> Text(
            inline(block.text), color = color,
            style = BodyStyle.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = when (block.level) { 1 -> 22.sp; 2 -> 20.sp; 3 -> 18.sp; else -> 16.sp },
                lineHeight = when (block.level) { 1 -> 30.sp; 2 -> 28.sp; 3 -> 26.sp; else -> 24.sp }
            ),
            modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp)
        )
        is MdBlock.Item -> Row(Modifier.padding(start = (block.depth.coerceAtMost(4) * 18).dp)) {
            Text(block.marker, color = AssistantTextSecondary, style = BodyStyle, modifier = Modifier.width(28.dp))
            Text(inline(block.text), color = color, style = BodyStyle, modifier = Modifier.weight(1f))
        }
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(AssistantAccent.copy(alpha = 0.5f)))
            Text(
                inline(block.text), color = AssistantTextSecondary, style = BodyStyle.copy(fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(AssistantOutline))
        is MdBlock.Code -> CodeBlock(block.language, block.code)
        is MdBlock.Table -> TableBlock(block, color)
        is MdBlock.Picture -> PictureBlock(block)
    }
}

private val BodyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)

@Composable
private fun CodeBlock(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1F2E))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.ifBlank { "code" }, color = Color(0xFFB9BED3), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                if (copied) "Copied" else "Copy", color = Color(0xFFC7D0FF), fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                }.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Text(
            code, color = Color(0xFFEDEFF7), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
        )
    }
}

/**
 * A table whose columns have one width for every row (the widest cell, within limits), so the header lines up with
 * the data. Cells wrap inside their column, rows share the height of their tallest cell, and a wide table scrolls sideways.
 */
@Composable
private fun TableBlock(table: MdBlock.Table, color: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyMedium
    val header = style.copy(fontWeight = FontWeight.SemiBold)
    val widths = remember(table, style) {
        val all = listOf(table.header) + table.rows
        table.header.indices.map { col ->
            val longest = all.maxOf { row ->
                inline(row[col]).text.lines().maxOfOrNull { measurer.measure(it, header, softWrap = false).size.width } ?: 0
            }
            (with(density) { longest.toDp() } + 24.dp).coerceIn(84.dp, 260.dp)
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val total = widths.fold(0.dp) { sum, w -> sum + w }
    val rows = listOf(table.header) + table.rows
    Column(Modifier.horizontalScroll(rememberScrollState()).clip(shape).border(1.dp, AssistantOutline, shape)) {
        rows.forEachIndexed { rowIndex, cells ->
            val background = when {
                rowIndex == 0 -> AssistantSelectedBg
                rowIndex % 2 == 0 -> AssistantFieldBackground.copy(alpha = 0.6f)
                else -> Color.Transparent
            }
            Row(Modifier.height(IntrinsicSize.Min).background(background)) {
                cells.forEachIndexed { col, cell ->
                    Text(
                        inline(cell), color = color,
                        textAlign = table.aligns.getOrElse(col) { TextAlign.Start },
                        style = if (rowIndex == 0) header else style,
                        modifier = Modifier.width(widths[col]).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            if (rowIndex < rows.lastIndex) Box(Modifier.width(total).height(1.dp).background(AssistantOutline))
        }
    }
}

/** An image written as ![alt](address) on its own line; it loads on the phone like a link preview image. */
@Composable
private fun PictureBlock(picture: MdBlock.Picture) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, picture.url) { value = LinkPreviews.image(picture.url)?.asImageBitmap() }
    val shape = RoundedCornerShape(12.dp)
    val loaded = image
    if (loaded != null) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Image(
                loaded, contentDescription = picture.alt, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(shape).border(1.dp, AssistantOutline, shape).clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(picture.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
            if (picture.alt.isNotBlank()) Text(picture.alt, color = AssistantTextMuted, fontSize = 12.sp)
        }
    } else if (picture.alt.isNotBlank()) {
        Text(picture.alt, color = AssistantTextMuted, style = BodyStyle)
    }
}

// ------------------------------------------------------------------ parsing

private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val numberedRegex = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val ruleRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val tableSeparatorRegex = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
private val pictureRegex = Regex("""^!\[([^\]]*)]\((https?://\S+?)\)$""")
private val taskRegex = Regex("""^\[([ xX])]\s+(.*)$""")

/**
 * Parses a growing text. Everything up to the last blank line outside a code block is final (later text cannot change
 * how it is read), so it is parsed once and kept; only the unfinished end is parsed again on each update.
 */
internal class IncrementalMarkdown {
    private var finishedText = ""
    private var finished: List<MdBlock> = emptyList()

    fun update(text: String): List<MdBlock> {
        if (!text.startsWith(finishedText)) {
            finishedText = ""
            finished = emptyList()
        }
        val rest = text.substring(finishedText.length)
        val cut = finishedLength(rest)
        if (cut > 0) {
            finished = finished + parseMarkdown(rest.substring(0, cut))
            finishedText += rest.substring(0, cut)
        }
        val tail = text.substring(finishedText.length)
        return if (tail.isBlank()) finished else finished + parseMarkdown(tail)
    }

    /** Length of the part of [text] that ends with the last blank line which is not inside an open code fence. */
    private fun finishedLength(text: String): Int {
        var offset = 0
        var inFence = false
        var cut = 0
        for (line in text.split('\n')) {
            val end = offset + line.length + 1 // the line and its newline
            if (line.trim().startsWith("```")) inFence = !inFence
            else if (!inFence && line.trim().isEmpty() && end <= text.length) cut = end
            offset = end
        }
        return cut
    }
}

internal fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                blocks += MdBlock.Code(language, code.toString())
            }
            trimmed.isEmpty() -> flushParagraph()
            ruleRegex.matches(line) -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }
            headingRegex.matches(trimmed) -> {
                flushParagraph()
                val match = headingRegex.matchEntire(trimmed)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                val quote = StringBuilder(trimmed.removePrefix(">").trim())
                while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                    i++
                    quote.append('\n').append(lines[i].trim().removePrefix(">").trim())
                }
                blocks += MdBlock.Quote(quote.toString())
            }
            pictureRegex.matches(trimmed) -> {
                flushParagraph()
                val match = pictureRegex.matchEntire(trimmed)!!
                blocks += MdBlock.Picture(match.groupValues[1], match.groupValues[2])
            }
            trimmed.contains('|') && i + 1 < lines.size && tableSeparatorRegex.matches(lines[i + 1]) &&
                splitRow(trimmed).size == splitRow(lines[i + 1]).size && splitRow(trimmed).size > 1 -> {
                flushParagraph()
                val header = splitRow(trimmed)
                val aligns = splitRow(lines[i + 1]).map {
                    when {
                        it.startsWith(":") && it.endsWith(":") -> TextAlign.Center
                        it.endsWith(":") -> TextAlign.End
                        else -> TextAlign.Start
                    }
                }
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                    rows += splitRow(lines[i].trim()).let { row -> (row + List(header.size) { "" }).take(header.size) }
                    i++
                }
                i-- // the loop's own i++ moves past the last table row
                blocks += MdBlock.Table(header, rows, aligns)
            }
            bulletRegex.matches(line) -> {
                flushParagraph()
                val match = bulletRegex.matchEntire(line)!!
                val depth = match.groupValues[1].length / 2
                val task = taskRegex.matchEntire(match.groupValues[3])
                blocks += when {
                    task != null -> MdBlock.Item(if (task.groupValues[1] == " ") "\u2610" else "\u2611", depth, task.groupValues[2])
                    else -> MdBlock.Item(if (depth == 0) "\u2022" else "\u25E6", depth, match.groupValues[3])
                }
            }
            numberedRegex.matches(line) -> {
                flushParagraph()
                val match = numberedRegex.matchEntire(line)!!
                blocks += MdBlock.Item(match.groupValues[2] + ".", match.groupValues[1].length / 2, match.groupValues[3])
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val cellSplit = Regex("(?<!\\\\)\\|")

private fun splitRow(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split(cellSplit).map { it.replace("\\|", "|").trim() }

// ------------------------------------------------------------------ inline

private val inlineRegex = Regex(
    """`([^`\n]+)`|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\[([^\]\n]+)]\((https?://(?:[^()\s]|\([^()\s]*\))+)\)|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])|<(https?://[^>\s]+)>|(?<![(\w])(https?://(?:[^\s<>\[\]()]|\([^\s()]*\))*(?:[^\s<>\[\]().,;:!?'"*_]|\([^\s()]*\)))"""
)

private val lineBreakRegex = Regex("(?i)<br\\s*/?>")

private fun inline(text: String): AnnotatedString = buildAnnotatedString { appendInline(text.replace(lineBreakRegex, "\n"), 0) }

private fun AnnotatedString.Builder.appendInline(text: String, depth: Int) {
    var cursor = 0
    for (match in inlineRegex.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        val g = match.groups
        when {
            g[1] != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = AssistantSelectedBg, fontSize = 14.sp)
            ) { append(g[1]!!.value) }
            g[2] != null || g[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                if (depth < 3) appendInline((g[2] ?: g[3])!!.value, depth + 1) else append((g[2] ?: g[3])!!.value)
            }
            g[4] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(g[4]!!.value, depth + 1) }
            // Link text may carry its own bold or italics ("[**Title**](url)"), so it is parsed too.
            g[5] != null -> withLink(linkOf(g[6]!!.value)) {
                if (depth < 3) appendInline(g[5]!!.value, depth + 1) else append(g[5]!!.value)
            }
            // A bare address, or one in angle brackets, is a link too and is shown as written.
            g[9] != null || g[10] != null -> (g[9] ?: g[10])!!.value.let { url -> withLink(linkOf(url)) { append(url) } }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                val inner = (g[7] ?: g[8])!!.value
                if (depth < 3) appendInline(inner, depth + 1) else append(inner)
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private fun linkOf(url: String) =
    LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = AssistantAccent, textDecoration = TextDecoration.Underline)))

private fun AnnotatedString.Builder.withStyle(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style)
    block()
    pop()
}
