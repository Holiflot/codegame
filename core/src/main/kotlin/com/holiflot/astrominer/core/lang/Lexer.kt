package com.holiflot.astrominer.core.lang

enum class TokenType { NAME, KEYWORD, INT, FLOAT, STRING, OP, NEWLINE, INDENT, DEDENT, EOF }

data class Token(val type: TokenType, val text: String, val line: Int)

/** Oyuncunun yazdığı hata; [line] 0 ise satır bilinmiyor demektir. */
class ScriptError(message: String, val line: Int = 0) : Exception(message) {
    override val message: String get() = super.message ?: ""

    fun display(): String = if (line > 0) "Satır $line: $message" else message
}

val KEYWORDS = setOf(
    "if", "elif", "else", "while", "for", "in", "def", "return", "break", "continue",
    "pass", "and", "or", "not", "True", "False", "None", "global",
)

private val OPERATORS = listOf(
    "**=", "//=",
    "**", "//", "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=",
    "+", "-", "*", "/", "%", "<", ">", "=", "(", ")", "[", "]", ",", ":", ".",
)

/**
 * Python benzeri girinti tabanlı sözdizimini token listesine çevirir.
 * Girinti değişimleri INDENT / DEDENT token'ları olarak üretilir.
 */
class Lexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private val indents = ArrayDeque<Int>().apply { addLast(0) }
    private var depth = 0

    fun tokenize(): List<Token> {
        val lines = source.replace("\r\n", "\n").split("\n")
        var lastLine = 1
        for ((index, raw) in lines.withIndex()) {
            val lineNo = index + 1
            lastLine = lineNo
            var start = 0
            if (depth == 0) {
                var width = 0
                while (start < raw.length && (raw[start] == ' ' || raw[start] == '\t')) {
                    width += if (raw[start] == '\t') 4 else 1
                    start++
                }
                if (start >= raw.length || raw[start] == '#') continue
                handleIndent(width, lineNo)
            }
            scanLine(raw, start, lineNo)
            if (depth == 0 && tokens.isNotEmpty() && tokens.last().type != TokenType.NEWLINE) {
                tokens += Token(TokenType.NEWLINE, "", lineNo)
            }
        }
        if (depth > 0) throw ScriptError("Kapanmamış parantez", lastLine)
        while (indents.size > 1) {
            indents.removeLast()
            tokens += Token(TokenType.DEDENT, "", lastLine)
        }
        tokens += Token(TokenType.EOF, "", lastLine)
        return tokens
    }

    private fun handleIndent(width: Int, line: Int) {
        val current = indents.last()
        if (width > current) {
            indents.addLast(width)
            tokens += Token(TokenType.INDENT, "", line)
            return
        }
        while (width < indents.last()) {
            indents.removeLast()
            tokens += Token(TokenType.DEDENT, "", line)
        }
        if (width != indents.last()) throw ScriptError("Girinti önceki satırlarla uyuşmuyor", line)
    }

    private fun scanLine(raw: String, start: Int, line: Int) {
        var i = start
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == ' ' || c == '\t' -> i++
                c == '#' -> return
                c.isDigit() -> i = scanNumber(raw, i, line)
                c.isLetter() || c == '_' -> {
                    val end = scanWhile(raw, i) { it.isLetterOrDigit() || it == '_' }
                    val word = raw.substring(i, end)
                    val type = if (word in KEYWORDS) TokenType.KEYWORD else TokenType.NAME
                    tokens += Token(type, word, line)
                    i = end
                }
                c == '"' || c == '\'' -> i = scanString(raw, i, line)
                else -> {
                    val op = OPERATORS.firstOrNull { raw.startsWith(it, i) }
                        ?: throw ScriptError("Beklenmeyen karakter '$c'", line)
                    when (op) {
                        "(", "[" -> depth++
                        ")", "]" -> {
                            if (depth == 0) throw ScriptError("Fazladan '$op'", line)
                            depth--
                        }
                    }
                    tokens += Token(TokenType.OP, op, line)
                    i += op.length
                }
            }
        }
    }

    private fun scanNumber(raw: String, start: Int, line: Int): Int {
        var end = scanWhile(raw, start) { it.isDigit() }
        if (end + 1 < raw.length && raw[end] == '.' && raw[end + 1].isDigit()) {
            end = scanWhile(raw, end + 1) { it.isDigit() }
            tokens += Token(TokenType.FLOAT, raw.substring(start, end), line)
        } else {
            tokens += Token(TokenType.INT, raw.substring(start, end), line)
        }
        return end
    }

    private fun scanString(raw: String, start: Int, line: Int): Int {
        val quote = raw[start]
        val sb = StringBuilder()
        var i = start + 1
        while (i < raw.length) {
            val c = raw[i]
            if (c == quote) {
                tokens += Token(TokenType.STRING, sb.toString(), line)
                return i + 1
            }
            if (c == '\\' && i + 1 < raw.length) {
                sb.append(
                    when (val e = raw[i + 1]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> e
                    },
                )
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        throw ScriptError("Kapanmamış metin (tırnak eksik)", line)
    }

    private inline fun scanWhile(raw: String, start: Int, predicate: (Char) -> Boolean): Int {
        var i = start
        while (i < raw.length && predicate(raw[i])) i++
        return i
    }
}
