package com.holiflot.astrominer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holiflot.astrominer.core.game.Commands
import com.holiflot.astrominer.core.lang.KEYWORDS
import com.holiflot.astrominer.ui.theme.Palette

private val LINE_HEIGHT = 20.sp
private val EDITOR_PADDING = 8.dp

private val codeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = LINE_HEIGHT,
    color = Palette.text,
)

/**
 * Mobil için sade bir kod editörü: satır numaraları, sözdizimi renklendirme, satır
 * kaydırmasız yatay kaydırma ve çalışan satırı görünür tutma.
 */
@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    currentLine: Int?,
    errorLines: Set<Int>,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = value.text.count { it == '\n' } + 1
    val lineHeightPx = with(LocalDensity.current) { LINE_HEIGHT.toPx() }
    val paddingPx = with(LocalDensity.current) { EDITOR_PADDING.toPx() }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.panel),
    ) {
        val viewportHeight = maxHeight
        val viewportPx = constraints.maxHeight

        // Çalışan satır ekran dışına çıkarsa kaydır.
        LaunchedEffect(currentLine) {
            val line = currentLine ?: return@LaunchedEffect
            val top = (paddingPx + (line - 1) * lineHeightPx).toInt()
            val bottom = (top + lineHeightPx + paddingPx).toInt()
            when {
                top < verticalScroll.value -> verticalScroll.animateScrollTo(top)
                bottom > verticalScroll.value + viewportPx -> verticalScroll.animateScrollTo(bottom - viewportPx)
            }
        }

        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .padding(vertical = EDITOR_PADDING),
        ) {
            Text(
                text = gutter(lineCount, currentLine, errorLines),
                style = codeStyle.copy(color = Palette.textDim),
                modifier = Modifier.padding(start = 6.dp, end = 8.dp),
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                val fieldMinWidth = maxWidth
                Box(Modifier.horizontalScroll(horizontalScroll)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        readOnly = readOnly,
                        textStyle = codeStyle,
                        cursorBrush = SolidColor(Palette.crystalGrown),
                        visualTransformation = SyntaxHighlighter,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier
                            .widthIn(min = fieldMinWidth)
                            .heightIn(min = viewportHeight - EDITOR_PADDING * 2)
                            .padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

private fun gutter(lineCount: Int, currentLine: Int?, errorLines: Set<Int>): AnnotatedString {
    val width = lineCount.toString().length
    return buildAnnotatedString {
        for (i in 1..lineCount) {
            val label = i.toString().padStart(width)
            when (i) {
                currentLine -> withStyle(SpanStyle(color = Palette.drone, fontWeight = FontWeight.Bold)) {
                    append("▶$label")
                }
                in errorLines -> withStyle(SpanStyle(color = Palette.error, fontWeight = FontWeight.Bold)) {
                    append("!$label")
                }
                else -> append(" $label")
            }
            if (i < lineCount) append('\n')
        }
    }
}

private val COMMAND_NAMES = Commands.all.map { it.name }.toSet() +
    setOf("print", "str", "abs", "min", "max", "len", "range")

private val TOKEN_REGEX = Regex(
    """(#.*)|("(?:[^"\\\n]|\\.)*"?|'(?:[^'\\\n]|\\.)*'?)|\b(\d+(?:\.\d+)?)\b|\b([A-Za-z_]\w*)\b""",
)

/** Basit, regex tabanlı Python renklendirmesi. Metni değiştirmez; sadece stil ekler. */
private object SyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    private fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        for (match in TOKEN_REGEX.findAll(code)) {
            val color = when {
                match.groups[1] != null -> Palette.comment
                match.groups[2] != null -> Palette.string
                match.groups[3] != null -> Palette.number
                else -> {
                    val word = match.value
                    when {
                        word in KEYWORDS -> Palette.keyword
                        word in COMMAND_NAMES && word.first().isLowerCase() -> Palette.command
                        word.first().isUpperCase() -> Palette.constant
                        else -> null
                    }
                }
            } ?: continue
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
        }
    }
}
