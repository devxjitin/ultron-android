package com.assistant.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.ui.theme.AssistantAccent
import com.assistant.app.ui.theme.AssistantFieldBackground
import com.assistant.app.ui.theme.AssistantOutline
import com.assistant.app.ui.theme.AssistantTextMuted
import com.assistant.app.ui.theme.AssistantTextPrimary
import com.assistant.app.ui.theme.AssistantTextSecondary

/** One block of a Markdown document, already split from the raw text. */
private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Item(val depth: Int, val marker: String, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val TABLE_DIVIDER = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/** Splits Markdown text into blocks. Handles what skills use: headings, lists, code, quotes, rules, tables and paragraphs. */
private fun parseMarkdown(text: String): List<MdBlock> {
    val lines = text.replace("\r\n", "\n").lines()
    val blocks = ArrayList<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            line.trimStart().startsWith("```") -> {
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) code.add(lines[i++])
                i++
                blocks.add(MdBlock.Code(code.joinToString("\n")))
            }
            HEADING.matches(line) -> {
                val m = HEADING.matchEntire(line)!!
                blocks.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())); i++
            }
            RULE.matches(line) -> { blocks.add(MdBlock.Rule); i++ }
            line.trimStart().startsWith(">") -> {
                val quote = ArrayList<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) quote.add(lines[i++].trimStart().removePrefix(">").trim())
                blocks.add(MdBlock.Quote(quote.joinToString(" ")))
            }
            line.contains('|') && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) -> {
                val rows = arrayListOf(tableCells(line))
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows.add(tableCells(lines[i++]))
                blocks.add(MdBlock.Table(rows))
            }
            ITEM.matches(line) -> {
                val m = ITEM.matchEntire(line)!!
                val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                var body = m.groupValues[3].trim()
                i++
                // A wrapped line indented under an item belongs to that item.
                while (i < lines.size && lines[i].isNotBlank() && lines[i].startsWith("  ") && !ITEM.matches(lines[i])) body += " " + lines[i++].trim()
                blocks.add(MdBlock.Item(m.groupValues[1].length / 2, marker, body))
            }
            else -> {
                val para = StringBuilder(line.trim())
                i++
                while (i < lines.size && lines[i].isNotBlank() && !HEADING.matches(lines[i]) && !ITEM.matches(lines[i]) &&
                    !lines[i].trimStart().startsWith("```") && !lines[i].trimStart().startsWith(">") && !RULE.matches(lines[i])
                ) para.append(' ').append(lines[i++].trim())
                blocks.add(MdBlock.Paragraph(para.toString()))
            }
        }
    }
    return blocks
}

/** Turns inline Markdown (**bold**, *italic*, `code`, [link](url)) into styled text. */
private fun inline(text: String, codeBg: androidx.compose.ui.graphics.Color, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    fun closing(token: String, from: Int) = text.indexOf(token, from).takeIf { it > from }
    while (i < text.length) {
        val c = text[i]
        when {
            text.startsWith("**", i) && closing("**", i + 2) != null -> {
                val end = closing("**", i + 2)!!
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            c == '`' && closing("`", i + 1) != null -> {
                val end = closing("`", i + 1)!!
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 0.92.em())) { append(" ${text.substring(i + 1, end)} ") }
                i = end + 1
            }
            (c == '*' || c == '_') && i + 1 < text.length && text[i + 1] != ' ' && closing(c.toString(), i + 1) != null &&
                (i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                val end = closing(c.toString(), i + 1)!!
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            c == '[' -> {
                val close = text.indexOf("](", i)
                val paren = if (close > 0) text.indexOf(')', close) else -1
                if (close > 0 && paren > 0) {
                    withStyle(SpanStyle(color = linkColor)) { append(text.substring(i + 1, close)) }
                    i = paren + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}

private fun Double.em() = (this * 14).sp

/** Shows Markdown the way a preview does: real headings, bullets, bold, code blocks and tables instead of the raw symbols. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val codeBg = AssistantFieldBackground
    val link = AssistantAccent
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, codeBg, link), color = AssistantTextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 17.sp; else -> 15.sp },
                    lineHeight = when (block.level) { 1 -> 26.sp; 2 -> 23.sp; else -> 21.sp },
                    modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp)
                )
                is MdBlock.Paragraph -> Text(inline(block.text, codeBg, link), color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
                is MdBlock.Item -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(block.marker, color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.width(22.dp))
                    Text(inline(block.text, codeBg, link), color = AssistantTextSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(codeBg)
                        .border(BorderStroke(1.dp, AssistantOutline), RoundedCornerShape(12.dp)).horizontalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    Text(block.text, color = AssistantTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, softWrap = false)
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(AssistantAccent))
                    Text(inline(block.text, codeBg, link), color = AssistantTextMuted, fontSize = 14.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(start = 12.dp))
                }
                is MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(AssistantOutline))
                is MdBlock.Table -> MarkdownTable(block.rows, codeBg, link)
            }
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>, codeBg: androidx.compose.ui.graphics.Color, link: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, AssistantOutline), shape).horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { index, cells ->
            Row(Modifier.background(if (index == 0) codeBg else androidx.compose.ui.graphics.Color.Transparent)) {
                cells.forEach { cell ->
                    Text(
                        inline(cell, codeBg, link), color = if (index == 0) AssistantTextPrimary else AssistantTextSecondary,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.widthIn(min = 90.dp, max = 220.dp).padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            if (index < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(AssistantOutline))
        }
    }
}
