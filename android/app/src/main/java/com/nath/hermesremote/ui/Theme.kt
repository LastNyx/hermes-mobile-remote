package com.nath.hermesremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Gold = Color(0xFFE8B54A)
val Ok = Color(0xFF5BC27A)
val Bad = Color(0xFFE5675C)
val Warn = Color(0xFFE0A53A)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A05),
    secondary = Color(0xFF9DB4D6),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF22252C),
    surfaceContainer = Color(0xFF1A1C21),
    surfaceContainerHigh = Color(0xFF22252C),
    surfaceContainerHighest = Color(0xFF2A2D35),
    onSurface = Color(0xFFE6E6E9),
    onSurfaceVariant = Color(0xFFA9ABB3),
    outline = Color(0xFF3A3E47),
    error = Bad,
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

/** Minimal markdown: fenced code blocks, headings, bullets, **bold**, *italic*, `code`. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { splitFences(text) }
    SelectionContainer(modifier) {
        Column {
            blocks.forEach { (isCode, body) ->
                if (isCode) {
                    Text(
                        body.trimEnd('\n'),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        softWrap = false,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                } else if (body.isNotBlank()) {
                    Text(inline(body.trim('\n')), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp))
                }
            }
        }
    }
}

private fun splitFences(text: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    val parts = text.split("```")
    parts.forEachIndexed { i, p ->
        if (i % 2 == 1) out += true to p.substringAfter('\n', p) else out += false to p
    }
    return out
}

private val inlineRe = Regex("""\*\*(.+?)\*\*|`([^`]+)`|(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])""")

private fun inline(src: String): AnnotatedString = buildAnnotatedString {
    src.lines().forEachIndexed { li, raw ->
        if (li > 0) append('\n')
        var line = raw
        val heading = Regex("^#{1,6}\\s+").find(line)
        if (heading != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) { appendInline(line.substring(heading.range.last + 1)) }
            return@forEachIndexed
        }
        Regex("^(\\s*)[-*+]\\s+").find(line)?.let { m ->
            append(m.groupValues[1] + "•  ")
            line = line.substring(m.range.last + 1)
        }
        appendInline(line)
    }
}

private fun AnnotatedString.Builder.appendInline(s: String) {
    var i = 0
    for (m in inlineRe.findAll(s)) {
        append(s.substring(i, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF2A2D35), fontSize = 13.sp)) { append(m.groupValues[2]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[3]) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
