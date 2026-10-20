package com.assistant.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Item(val marker: String, val depth: Int, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

/**
 * Renders a chat reply written in Markdown: headings, bold/italic/strike, inline and fenced code,
 * lists, quotes, links, rules and tables. Text that is still streaming (an unclosed `**` or code
 * fence) simply shows as it is until the closing part arrives.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = AssistantTextPrimary) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
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
                is MdBlock.Item -> Row(Modifier.padding(start = (block.depth * 18).dp)) {
                    Text(block.marker, color = AssistantTextSecondary, style = BodyStyle, modifier = Modifier.width(24.dp))
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
            }
        }
    }
}

private val BodyStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)

@Composable
private fun CodeBlock(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B1A1E))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.ifBlank { "code" }, color = Color(0xFFD9B8BE), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                if (copied) "Copied" else "Copy", color = Color(0xFFFFB4BE), fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                }.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Text(
            code, color = Color(0xFFF5E6E8), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
        )
    }
}

@Composable
private fun TableBlock(table: MdBlock.Table, color: Color) {
    Column(
        Modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(10.dp))
            .border(1.dp, AssistantOutline, RoundedCornerShape(10.dp))
    ) {
        (listOf(table.header) + table.rows).forEachIndexed { rowIndex, cells ->
            Row(Modifier.background(if (rowIndex == 0) AssistantSelectedBg else Color.Transparent)) {
                cells.forEach { cell ->
                    Text(
                        inline(cell), color = color,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal),
                        modifier = Modifier.widthIn(min = 80.dp, max = 220.dp).padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ parsing

private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val numberedRegex = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val ruleRegex = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val tableSeparatorRegex = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")

private fun parseMarkdown(source: String): List<MdBlock> {
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
            trimmed.startsWith("|") && i + 1 < lines.size && tableSeparatorRegex.matches(lines[i + 1]) -> {
                flushParagraph()
                val header = splitRow(trimmed)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    rows += splitRow(lines[i].trim()).let { row -> (row + List(header.size) { "" }).take(header.size) }
                    i++
                }
                i-- // the loop's own i++ moves past the last table row
                blocks += MdBlock.Table(header, rows)
            }
            bulletRegex.matches(line) -> {
                flushParagraph()
                val match = bulletRegex.matchEntire(line)!!
                blocks += MdBlock.Item("•", match.groupValues[1].length / 2, match.groupValues[3])
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

private fun splitRow(row: String): List<String> =
    row.trim().trim('|').split('|').map { it.trim() }

// ------------------------------------------------------------------ inline

private val inlineRegex = Regex(
    """`([^`\n]+)`|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\[([^\]\n]+)]\((https?://[^)\s]+)\)|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])"""
)

private fun inline(text: String): AnnotatedString = buildAnnotatedString { appendInline(text, 0) }

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
            g[5] != null -> withLink(
                LinkAnnotation.Url(
                    g[6]!!.value,
                    TextLinkStyles(SpanStyle(color = AssistantAccent, textDecoration = TextDecoration.Underline))
                )
            ) { append(g[5]!!.value) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                val inner = (g[7] ?: g[8])!!.value
                if (depth < 3) appendInline(inner, depth + 1) else append(inner)
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

private fun AnnotatedString.Builder.withStyle(style: SpanStyle, block: AnnotatedString.Builder.() -> Unit) {
    pushStyle(style)
    block()
    pop()
}
