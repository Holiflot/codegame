package com.holiflot.astrominer.core.lang

private val AUG_OPS = setOf("+=", "-=", "*=", "/=", "//=", "%=", "**=")
private val COMPARE_OPS = setOf("==", "!=", "<", ">", "<=", ">=")

/** Özyinelemeli iniş (recursive descent) ayrıştırıcı. */
class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private var loopDepth = 0
    private var functionDepth = 0

    fun parseProgram(): Program {
        val body = mutableListOf<Stmt>()
        skipNewlines()
        while (!check(TokenType.EOF)) {
            if (check(TokenType.INDENT)) throw error("Beklenmeyen girinti")
            body += statement()
            skipNewlines()
        }
        return Program(body)
    }

    // ---- Statements ----

    private fun statement(): Stmt = when {
        isKeyword("if") -> ifStatement()
        isKeyword("while") -> {
            val line = advance().line
            val condition = expression()
            expectOp(":")
            While(condition, loopBody(), line)
        }
        isKeyword("for") -> {
            val line = advance().line
            val variable = expect(TokenType.NAME, "Değişken adı bekleniyordu").text
            expectKeyword("in")
            val iterable = expression()
            expectOp(":")
            For(variable, iterable, loopBody(), line)
        }
        isKeyword("def") -> functionDef()
        else -> simpleStatement().also { endOfStatement() }
    }

    private fun ifStatement(): Stmt {
        val line = advance().line // if / elif
        val condition = expression()
        expectOp(":")
        val body = block()
        val orElse = when {
            isKeyword("elif") -> listOf(ifStatement())
            isKeyword("else") -> {
                advance()
                expectOp(":")
                block()
            }
            else -> emptyList()
        }
        return If(condition, body, orElse, line)
    }

    private fun functionDef(): Stmt {
        val line = advance().line
        val name = expect(TokenType.NAME, "Fonksiyon adı bekleniyordu").text
        expectOp("(")
        val params = mutableListOf<String>()
        if (!isOp(")")) {
            do {
                params += expect(TokenType.NAME, "Parametre adı bekleniyordu").text
            } while (matchOp(","))
        }
        expectOp(")")
        expectOp(":")
        val savedLoops = loopDepth
        loopDepth = 0
        functionDepth++
        val body = block()
        functionDepth--
        loopDepth = savedLoops
        return Def(name, params, body, line)
    }

    private fun loopBody(): List<Stmt> {
        loopDepth++
        try {
            return block()
        } finally {
            loopDepth--
        }
    }

    private fun block(): List<Stmt> {
        if (!match(TokenType.NEWLINE)) {
            // Tek satırlık gövde: `if x: mine()`
            return listOf(simpleStatement().also { endOfStatement() })
        }
        if (!match(TokenType.INDENT)) throw error("Girintili bir blok bekleniyordu")
        val body = mutableListOf<Stmt>()
        while (!check(TokenType.DEDENT) && !check(TokenType.EOF)) {
            body += statement()
            skipNewlines()
        }
        match(TokenType.DEDENT)
        return body
    }

    private fun simpleStatement(): Stmt {
        val line = peek().line
        when {
            isKeyword("pass") -> {
                advance()
                return Pass(line)
            }
            isKeyword("break") -> {
                advance()
                if (loopDepth == 0) throw ScriptError("'break' sadece döngü içinde kullanılabilir", line)
                return Break(line)
            }
            isKeyword("continue") -> {
                advance()
                if (loopDepth == 0) throw ScriptError("'continue' sadece döngü içinde kullanılabilir", line)
                return Continue(line)
            }
            isKeyword("return") -> {
                advance()
                if (functionDepth == 0) throw ScriptError("'return' sadece fonksiyon içinde kullanılabilir", line)
                val value = if (atStatementEnd()) null else expression()
                return Return(value, line)
            }
            isKeyword("global") -> {
                advance()
                val names = mutableListOf<String>()
                do {
                    names += expect(TokenType.NAME, "Değişken adı bekleniyordu").text
                } while (matchOp(","))
                return Global(names, line)
            }
        }
        val expr = expression()
        if (matchOp("=")) {
            checkTarget(expr)
            return Assign(expr, expression(), line)
        }
        val aug = peek().takeIf { it.type == TokenType.OP && it.text in AUG_OPS }
        if (aug != null) {
            advance()
            checkTarget(expr)
            return AugAssign(expr, aug.text.dropLast(1), expression(), line)
        }
        return ExprStmt(expr, line)
    }

    private fun checkTarget(expr: Expr) {
        if (expr !is Name && expr !is Index) throw ScriptError("Bu ifadeye değer atanamaz", expr.line)
    }

    private fun atStatementEnd() = check(TokenType.NEWLINE) || check(TokenType.EOF)

    private fun endOfStatement() {
        if (check(TokenType.EOF) || check(TokenType.DEDENT)) return
        if (!match(TokenType.NEWLINE)) throw error("Satır sonu bekleniyordu, '${peek().text}' bulundu")
    }

    // ---- Expressions ----

    private fun expression(): Expr = orExpr()

    private fun orExpr(): Expr {
        var left = andExpr()
        while (isKeyword("or")) {
            val line = advance().line
            left = BoolOp("or", left, andExpr(), line)
        }
        return left
    }

    private fun andExpr(): Expr {
        var left = notExpr()
        while (isKeyword("and")) {
            val line = advance().line
            left = BoolOp("and", left, notExpr(), line)
        }
        return left
    }

    private fun notExpr(): Expr {
        if (isKeyword("not")) {
            val line = advance().line
            return Unary("not", notExpr(), line)
        }
        return comparison()
    }

    private fun comparison(): Expr {
        val left = arith()
        val ops = mutableListOf<String>()
        val rights = mutableListOf<Expr>()
        while (true) {
            val t = peek()
            val op = when {
                t.type == TokenType.OP && t.text in COMPARE_OPS -> t.text.also { advance() }
                isKeyword("in") -> "in".also { advance() }
                isKeyword("not") && peek(1).let { it.type == TokenType.KEYWORD && it.text == "in" } -> {
                    advance()
                    advance()
                    "not in"
                }
                else -> break
            }
            ops += op
            rights += arith()
        }
        return if (ops.isEmpty()) left else Compare(left, ops, rights, left.line)
    }

    private fun arith(): Expr {
        var left = term()
        while (isOp("+") || isOp("-")) {
            val op = advance()
            left = Binary(op.text, left, term(), op.line)
        }
        return left
    }

    private fun term(): Expr {
        var left = factor()
        while (isOp("*") || isOp("/") || isOp("//") || isOp("%")) {
            val op = advance()
            left = Binary(op.text, left, factor(), op.line)
        }
        return left
    }

    private fun factor(): Expr {
        if (isOp("-") || isOp("+")) {
            val op = advance()
            return Unary(op.text, factor(), op.line)
        }
        return power()
    }

    private fun power(): Expr {
        val base = postfix()
        if (isOp("**")) {
            val op = advance()
            return Binary("**", base, factor(), op.line)
        }
        return base
    }

    private fun postfix(): Expr {
        var expr = atom()
        while (true) {
            expr = when {
                isOp("(") -> {
                    val line = advance().line
                    val args = mutableListOf<Expr>()
                    if (!isOp(")")) {
                        do {
                            if (isOp(")")) break
                            args += expression()
                        } while (matchOp(","))
                    }
                    expectOp(")")
                    Call(expr, args, line)
                }
                isOp("[") -> {
                    val line = advance().line
                    val index = expression()
                    expectOp("]")
                    Index(expr, index, line)
                }
                isOp(".") -> {
                    val line = advance().line
                    Attribute(expr, expect(TokenType.NAME, "'.' sonrası isim bekleniyordu").text, line)
                }
                else -> return expr
            }
        }
    }

    private fun atom(): Expr {
        val t = peek()
        return when (t.type) {
            TokenType.INT -> {
                advance()
                IntLit(t.text.toLongOrNull() ?: throw ScriptError("Sayı çok büyük", t.line), t.line)
            }
            TokenType.FLOAT -> {
                advance()
                FloatLit(t.text.toDouble(), t.line)
            }
            TokenType.STRING -> {
                advance()
                StrLit(t.text, t.line)
            }
            TokenType.NAME -> {
                advance()
                Name(t.text, t.line)
            }
            TokenType.KEYWORD -> when (t.text) {
                "True" -> BoolLit(true, advance().line)
                "False" -> BoolLit(false, advance().line)
                "None" -> NoneLit(advance().line)
                else -> throw error("Burada '${t.text}' kullanılamaz")
            }
            TokenType.OP -> when (t.text) {
                "(" -> {
                    advance()
                    expression().also { expectOp(")") }
                }
                "[" -> {
                    advance()
                    val items = mutableListOf<Expr>()
                    if (!isOp("]")) {
                        do {
                            if (isOp("]")) break
                            items += expression()
                        } while (matchOp(","))
                    }
                    expectOp("]")
                    ListLit(items, t.line)
                }
                else -> throw error("Beklenmeyen '${t.text}'")
            }
            TokenType.NEWLINE, TokenType.EOF -> throw error("Satır yarım kaldı, bir ifade bekleniyordu")
            TokenType.INDENT -> throw error("Beklenmeyen girinti")
            TokenType.DEDENT -> throw error("Beklenmeyen girinti azalması")
        }
    }

    // ---- Helpers ----

    private fun peek(offset: Int = 0): Token = tokens[minOf(pos + offset, tokens.size - 1)]
    private fun advance(): Token = tokens[pos].also { if (pos < tokens.size - 1) pos++ }
    private fun check(type: TokenType) = peek().type == type
    private fun match(type: TokenType): Boolean = check(type).also { if (it) advance() }
    private fun isOp(text: String) = peek().let { it.type == TokenType.OP && it.text == text }
    private fun isKeyword(text: String) = peek().let { it.type == TokenType.KEYWORD && it.text == text }
    private fun matchOp(text: String): Boolean = isOp(text).also { if (it) advance() }

    private fun expectOp(text: String) {
        if (!matchOp(text)) throw error("'$text' bekleniyordu")
    }

    private fun expectKeyword(text: String) {
        if (!isKeyword(text)) throw error("'$text' bekleniyordu")
        advance()
    }

    private fun expect(type: TokenType, message: String): Token {
        if (!check(type)) throw error(message)
        return advance()
    }

    private fun skipNewlines() {
        while (match(TokenType.NEWLINE)) Unit
    }

    private fun error(message: String) = ScriptError(message, peek().line)

    companion object {
        fun parse(source: String): Program = Parser(Lexer(source).tokenize()).parseProgram()
    }
}
