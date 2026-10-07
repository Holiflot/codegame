package com.holiflot.astrominer.core.lang

/**
 * Oyuncunun satın alarak açtığı dil özellikleri. [unlockId], progress katmanındaki
 * kilit kimliğiyle eşleşir.
 */
enum class Feature(val keyword: String, val unlockId: String) {
    WHILE("while", "while"),
    IF("if / else", "if"),
    OPERATORS("operatörler (+ - == < and not ...)", "operators"),
    VARIABLES("değişkenler (x = ...)", "variables"),
    FOR("for / range", "for"),
    FUNCTIONS("def / return", "functions"),
    LISTS("listeler ([ ])", "lists"),
}

/**
 * Programı çalıştırmadan önce, henüz açılmamış dil özelliklerini ve komutları bulur.
 * Her özellik için yalnızca ilk kullanım raporlanır.
 *
 * @param lockedNames henüz açılmamış komut/sabit adı → kilidin başlığı
 * @param featureTitles özellik → kilidin başlığı (hata mesajında gösterilir)
 */
class FeatureChecker(
    private val allowed: Set<Feature>,
    private val lockedNames: Map<String, String>,
    private val featureTitles: Map<Feature, String> = emptyMap(),
) {
    private val errors = mutableListOf<ScriptError>()
    private val reportedFeatures = mutableSetOf<Feature>()
    private val reportedNames = mutableSetOf<String>()

    fun check(program: Program): List<ScriptError> {
        program.body.forEach(::stmt)
        return errors.sortedBy { it.line }
    }

    private fun require(feature: Feature, line: Int) {
        if (feature in allowed || !reportedFeatures.add(feature)) return
        val title = featureTitles[feature]
        val hint = if (title != null) " ('$title' kilidini aç)" else ""
        errors += ScriptError("${feature.keyword} henüz açılmadı$hint", line)
    }

    private fun stmt(s: Stmt) {
        when (s) {
            is ExprStmt -> expr(s.expr)
            is Assign -> {
                require(Feature.VARIABLES, s.line)
                assignTarget(s.target)
                expr(s.value)
            }
            is AugAssign -> {
                require(Feature.VARIABLES, s.line)
                require(Feature.OPERATORS, s.line)
                assignTarget(s.target)
                expr(s.value)
            }
            is If -> {
                require(Feature.IF, s.line)
                expr(s.condition)
                s.body.forEach(::stmt)
                s.orElse.forEach(::stmt)
            }
            is While -> {
                require(Feature.WHILE, s.line)
                expr(s.condition)
                s.body.forEach(::stmt)
            }
            is For -> {
                require(Feature.FOR, s.line)
                require(Feature.VARIABLES, s.line)
                expr(s.iterable)
                s.body.forEach(::stmt)
            }
            is Def -> {
                require(Feature.FUNCTIONS, s.line)
                s.body.forEach(::stmt)
            }
            is Return -> {
                require(Feature.FUNCTIONS, s.line)
                s.value?.let(::expr)
            }
            is Global -> require(Feature.FUNCTIONS, s.line)
            is Break, is Continue -> require(Feature.WHILE, s.line)
            is Pass -> Unit
        }
    }

    private fun assignTarget(target: Expr) {
        if (target is Index) {
            require(Feature.LISTS, target.line)
            expr(target.target)
            expr(target.index)
        }
    }

    private fun expr(e: Expr) {
        when (e) {
            is IntLit, is FloatLit, is StrLit, is BoolLit, is NoneLit -> Unit
            is Name -> {
                val title = lockedNames[e.name]
                if (title != null && reportedNames.add(e.name)) {
                    errors += ScriptError("${e.name} henüz açılmadı ('$title' kilidini aç)", e.line)
                }
            }
            is ListLit -> {
                require(Feature.LISTS, e.line)
                e.items.forEach(::expr)
            }
            is Unary -> {
                // `-1` gibi negatif sayılar her zaman serbest.
                val isNegativeLiteral = e.op == "-" && (e.operand is IntLit || e.operand is FloatLit)
                if (!isNegativeLiteral) require(Feature.OPERATORS, e.line)
                expr(e.operand)
            }
            is Binary -> {
                require(Feature.OPERATORS, e.line)
                expr(e.left)
                expr(e.right)
            }
            is Compare -> {
                require(Feature.OPERATORS, e.line)
                if (e.ops.any { it == "in" || it == "not in" }) require(Feature.LISTS, e.line)
                expr(e.left)
                e.rights.forEach(::expr)
            }
            is BoolOp -> {
                require(Feature.OPERATORS, e.line)
                expr(e.left)
                expr(e.right)
            }
            is Call -> {
                expr(e.callee)
                e.args.forEach(::expr)
            }
            is Attribute -> {
                if (e.target !is Name) require(Feature.LISTS, e.line)
                expr(e.target)
            }
            is Index -> {
                require(Feature.LISTS, e.line)
                expr(e.target)
                expr(e.index)
            }
        }
    }
}
