package com.holiflot.astrominer.core.lang

import kotlin.math.floor

/** Yorumlayıcının dış dünyayla (oyun, arayüz) konuştuğu kanal. */
interface InterpreterHost {
    /** Her ifade çalışmadan önce çağrılır: satır vurgusu, adım modu ve iptal noktası. */
    suspend fun onStatement(line: Int)

    fun print(text: String)
}

private object BreakSignal : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private object ContinueSignal : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private class ReturnSignal(val value: Value) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private class Frame {
    val locals = HashMap<String, Value>()
    val globalNames = HashSet<String>()
}

/**
 * AST üzerinde yürüyen, `suspend` tabanlı yorumlayıcı. Oyun komutları (move, mine...)
 * askıya alınabildiği için arayüz her eylemde animasyon oynatıp bekleyebilir.
 */
class Interpreter(
    private val host: InterpreterHost,
    extraBuiltins: Map<String, Value> = emptyMap(),
) {
    private val globals = HashMap<String, Value>()
    private val builtins = HashMap<String, Value>()
    private var callDepth = 0

    init {
        builtins.putAll(languageBuiltins())
        builtins.putAll(extraBuiltins)
    }

    suspend fun run(program: Program) {
        execBlock(program.body, null)
    }

    fun global(name: String): Value? = globals[name]

    // ---- Statements ----

    private suspend fun execBlock(body: List<Stmt>, frame: Frame?) {
        for (stmt in body) exec(stmt, frame)
    }

    private suspend fun exec(stmt: Stmt, frame: Frame?) {
        host.onStatement(stmt.line)
        when (stmt) {
            is ExprStmt -> eval(stmt.expr, frame)
            is Assign -> assign(stmt.target, eval(stmt.value, frame), frame)
            is AugAssign -> {
                val current = eval(stmt.target, frame)
                assign(stmt.target, binary(stmt.op, current, eval(stmt.value, frame), stmt.line), frame)
            }
            is If -> {
                if (truthy(eval(stmt.condition, frame))) execBlock(stmt.body, frame) else execBlock(stmt.orElse, frame)
            }
            is While -> {
                var first = true
                while (true) {
                    if (!first) host.onStatement(stmt.line)
                    first = false
                    if (!truthy(eval(stmt.condition, frame))) break
                    if (!runLoopBody(stmt.body, frame)) break
                }
            }
            is For -> {
                val items = iterate(eval(stmt.iterable, frame), stmt.line)
                for ((i, item) in items.withIndex()) {
                    if (i > 0) host.onStatement(stmt.line)
                    assignName(stmt.variable, item, frame)
                    if (!runLoopBody(stmt.body, frame)) break
                }
            }
            is Def -> assignName(stmt.name, FunctionValue(stmt), frame)
            is Return -> throw ReturnSignal(stmt.value?.let { eval(it, frame) } ?: NoneValue)
            is Global -> frame?.globalNames?.addAll(stmt.names)
            is Break -> throw BreakSignal
            is Continue -> throw ContinueSignal
            is Pass -> Unit
        }
    }

    /** Döngü gövdesini çalıştırır; `break` geldiyse false döner. */
    private suspend fun runLoopBody(body: List<Stmt>, frame: Frame?): Boolean {
        try {
            execBlock(body, frame)
        } catch (e: BreakSignal) {
            return false
        } catch (e: ContinueSignal) {
            // sonraki tura geç
        }
        return true
    }

    private fun iterate(value: Value, line: Int): List<Value> = when (value) {
        is ListValue -> value.items.toList()
        is StrValue -> value.value.map { StrValue(it.toString()) }
        else -> throw ScriptError("${typeName(value)} üzerinde döngü kurulamaz", line)
    }

    private suspend fun assign(target: Expr, value: Value, frame: Frame?) {
        when (target) {
            is Name -> assignName(target.name, value, frame)
            is Index -> {
                val list = eval(target.target, frame) as? ListValue
                    ?: throw ScriptError("Sadece listelerin elemanlarına atama yapılabilir", target.line)
                val i = listIndex(list, eval(target.index, frame), target.line)
                list.items[i] = value
            }
            else -> throw ScriptError("Bu ifadeye değer atanamaz", target.line)
        }
    }

    private fun assignName(name: String, value: Value, frame: Frame?) {
        if (frame != null && name !in frame.globalNames) frame.locals[name] = value else globals[name] = value
    }

    private fun lookup(name: String, frame: Frame?, line: Int): Value {
        if (frame != null && name !in frame.globalNames) frame.locals[name]?.let { return it }
        return globals[name] ?: builtins[name] ?: throw ScriptError("'$name' tanımlı değil", line)
    }

    // ---- Expressions ----

    private suspend fun eval(expr: Expr, frame: Frame?): Value = when (expr) {
        is IntLit -> IntValue(expr.value)
        is FloatLit -> FloatValue(expr.value)
        is StrLit -> StrValue(expr.value)
        is BoolLit -> BoolValue.of(expr.value)
        is NoneLit -> NoneValue
        is Name -> lookup(expr.name, frame, expr.line)
        is ListLit -> ListValue(expr.items.mapTo(ArrayList()) { eval(it, frame) })
        is Unary -> unary(expr.op, eval(expr.operand, frame), expr.line)
        is Binary -> binary(expr.op, eval(expr.left, frame), eval(expr.right, frame), expr.line)
        is BoolOp -> {
            val left = eval(expr.left, frame)
            when (expr.op) {
                "and" -> if (!truthy(left)) left else eval(expr.right, frame)
                else -> if (truthy(left)) left else eval(expr.right, frame)
            }
        }
        is Compare -> {
            var left = eval(expr.left, frame)
            var result = true
            for (i in expr.ops.indices) {
                val right = eval(expr.rights[i], frame)
                if (!compare(expr.ops[i], left, right, expr.line)) {
                    result = false
                    break
                }
                left = right
            }
            BoolValue.of(result)
        }
        is Call -> {
            val callee = eval(expr.callee, frame)
            val args = expr.args.map { eval(it, frame) }
            call(callee, args, expr.line)
        }
        is Attribute -> attribute(eval(expr.target, frame), expr.name, expr.line)
        is Index -> {
            val target = eval(expr.target, frame)
            val index = eval(expr.index, frame)
            when (target) {
                is ListValue -> target.items[listIndex(target, index, expr.line)]
                is StrValue -> {
                    val chars = ListValue(target.value.map { StrValue(it.toString()) }.toMutableList())
                    chars.items[listIndex(chars, index, expr.line)]
                }
                else -> throw ScriptError("${typeName(target)} indekslenemez", expr.line)
            }
        }
    }

    private suspend fun call(callee: Value, args: List<Value>, line: Int): Value = when (callee) {
        is BuiltinFunction -> {
            if (args.size !in callee.arity) {
                throw ScriptError("${callee.name}() ${arityText(callee.arity)} argüman alır, ${args.size} verildi", line)
            }
            try {
                callee.impl(args)
            } catch (e: ScriptError) {
                if (e.line == 0) throw ScriptError(e.message, line) else throw e
            }
        }
        is FunctionValue -> callUser(callee.def, args, line)
        else -> throw ScriptError("${typeName(callee)} çağrılamaz", line)
    }

    private suspend fun callUser(def: Def, args: List<Value>, line: Int): Value {
        if (args.size != def.params.size) {
            throw ScriptError("${def.name}() ${def.params.size} argüman alır, ${args.size} verildi", line)
        }
        if (callDepth >= MAX_CALL_DEPTH) throw ScriptError("Çok fazla iç içe fonksiyon çağrısı (özyineleme?)", line)
        val frame = Frame()
        def.params.forEachIndexed { i, p -> frame.locals[p] = args[i] }
        callDepth++
        try {
            execBlock(def.body, frame)
        } catch (r: ReturnSignal) {
            return r.value
        } finally {
            callDepth--
        }
        return NoneValue
    }

    private fun attribute(target: Value, name: String, line: Int): Value = when (target) {
        is NamespaceValue -> target.members[name]
            ?: throw ScriptError("${target.name} içinde '$name' yok", line)
        is ListValue -> listMethod(target, name, line)
        else -> throw ScriptError("${typeName(target)} değerinde '$name' yok", line)
    }

    private fun listMethod(list: ListValue, name: String, line: Int): Value = when (name) {
        "append" -> BuiltinFunction("append", 1..1) { args ->
            list.items.add(args[0])
            NoneValue
        }
        "pop" -> BuiltinFunction("pop", 0..1) { args ->
            if (list.items.isEmpty()) throw ScriptError("Boş listeden pop yapılamaz")
            val i = if (args.isEmpty()) list.items.size - 1 else listIndex(list, args[0], 0)
            list.items.removeAt(i)
        }
        "insert" -> BuiltinFunction("insert", 2..2) { args ->
            val i = (args[0] as? IntValue)?.value?.toInt() ?: throw ScriptError("insert() indeksi tam sayı olmalı")
            list.items.add(i.coerceIn(0, list.items.size), args[1])
            NoneValue
        }
        else -> throw ScriptError("Listede '$name' yöntemi yok", line)
    }

    private fun listIndex(list: ListValue, index: Value, line: Int): Int {
        val raw = (index as? IntValue)?.value ?: throw ScriptError("İndeks tam sayı olmalı", line)
        val i = if (raw < 0) raw + list.items.size else raw
        if (i < 0 || i >= list.items.size) {
            throw ScriptError("İndeks $raw liste dışında (uzunluk ${list.items.size})", line)
        }
        return i.toInt()
    }

    // ---- Operators ----

    private fun unary(op: String, v: Value, line: Int): Value = when (op) {
        "not" -> BoolValue.of(!truthy(v))
        "-" -> when (v) {
            is IntValue -> IntValue(-v.value)
            is FloatValue -> FloatValue(-v.value)
            else -> throw ScriptError("${typeName(v)} negatif yapılamaz", line)
        }
        else -> if (v is IntValue || v is FloatValue) v else throw ScriptError("'+' sayı ister", line)
    }

    private fun binary(op: String, a: Value, b: Value, line: Int): Value {
        if (a is IntValue && b is IntValue) return intBinary(op, a.value, b.value, line)
        if (isNumber(a) && isNumber(b)) return floatBinary(op, toDouble(a), toDouble(b), line)
        return when {
            op == "+" && a is StrValue && b is StrValue -> StrValue(a.value + b.value)
            op == "+" && a is ListValue && b is ListValue -> ListValue((a.items + b.items).toMutableList())
            op == "*" && a is StrValue && b is IntValue -> StrValue(a.value.repeat(b.value.coerceIn(0, 10_000).toInt()))
            op == "*" && a is ListValue && b is IntValue -> {
                val n = b.value.coerceIn(0, 10_000).toInt()
                ListValue(MutableList(a.items.size * n) { a.items[it % a.items.size] })
            }
            else -> throw ScriptError("'$op' işlemi ${typeName(a)} ve ${typeName(b)} arasında yapılamaz", line)
        }
    }

    private fun intBinary(op: String, a: Long, b: Long, line: Int): Value = when (op) {
        "+" -> IntValue(a + b)
        "-" -> IntValue(a - b)
        "*" -> IntValue(a * b)
        "/" -> {
            if (b == 0L) throw ScriptError("Sıfıra bölme", line)
            FloatValue(a.toDouble() / b)
        }
        "//" -> {
            if (b == 0L) throw ScriptError("Sıfıra bölme", line)
            IntValue(Math.floorDiv(a, b))
        }
        "%" -> {
            if (b == 0L) throw ScriptError("Sıfıra bölme", line)
            IntValue(Math.floorMod(a, b))
        }
        "**" -> if (b >= 0) intPow(a, b) else FloatValue(Math.pow(a.toDouble(), b.toDouble()))
        else -> throw ScriptError("Bilinmeyen işlem '$op'", line)
    }

    private fun intPow(base: Long, exp: Long): Value {
        var result = 1L
        try {
            repeat(exp.coerceAtMost(64).toInt()) { result = Math.multiplyExact(result, base) }
        } catch (e: ArithmeticException) {
            return FloatValue(Math.pow(base.toDouble(), exp.toDouble()))
        }
        return if (exp > 64 && base !in -1L..1L) FloatValue(Math.pow(base.toDouble(), exp.toDouble())) else IntValue(result)
    }

    private fun floatBinary(op: String, a: Double, b: Double, line: Int): Value = when (op) {
        "+" -> FloatValue(a + b)
        "-" -> FloatValue(a - b)
        "*" -> FloatValue(a * b)
        "/" -> {
            if (b == 0.0) throw ScriptError("Sıfıra bölme", line)
            FloatValue(a / b)
        }
        "//" -> {
            if (b == 0.0) throw ScriptError("Sıfıra bölme", line)
            FloatValue(floor(a / b))
        }
        "%" -> {
            if (b == 0.0) throw ScriptError("Sıfıra bölme", line)
            FloatValue(a - b * floor(a / b))
        }
        "**" -> FloatValue(Math.pow(a, b))
        else -> throw ScriptError("Bilinmeyen işlem '$op'", line)
    }

    private fun compare(op: String, a: Value, b: Value, line: Int): Boolean = when (op) {
        "==" -> valuesEqual(a, b)
        "!=" -> !valuesEqual(a, b)
        "in", "not in" -> {
            val found = when (b) {
                is ListValue -> b.items.any { valuesEqual(it, a) }
                is StrValue -> a is StrValue && b.value.contains(a.value)
                else -> throw ScriptError("'in' sağında liste veya metin olmalı", line)
            }
            if (op == "in") found else !found
        }
        else -> {
            val c = when {
                isNumber(a) && isNumber(b) -> toDouble(a).compareTo(toDouble(b))
                a is StrValue && b is StrValue -> a.value.compareTo(b.value)
                else -> throw ScriptError("${typeName(a)} ile ${typeName(b)} karşılaştırılamaz", line)
            }
            when (op) {
                "<" -> c < 0
                ">" -> c > 0
                "<=" -> c <= 0
                else -> c >= 0
            }
        }
    }

    private fun isNumber(v: Value) = v is IntValue || v is FloatValue

    private fun arityText(r: IntRange) = if (r.first == r.last) "${r.first}" else "${r.first}-${r.last}"

    // ---- Built-in language functions ----

    private fun languageBuiltins(): Map<String, Value> = listOf(
        BuiltinFunction("print", 0..16) { args ->
            host.print(args.joinToString(" ") { it.toString() })
            NoneValue
        },
        BuiltinFunction("str", 1..1) { args -> StrValue(args[0].toString()) },
        BuiltinFunction("abs", 1..1) { args ->
            when (val v = args[0]) {
                is IntValue -> IntValue(kotlin.math.abs(v.value))
                else -> FloatValue(kotlin.math.abs(toDouble(v)))
            }
        },
        BuiltinFunction("min", 1..16) { args -> extreme(args, "min") { a, b -> a < b } },
        BuiltinFunction("max", 1..16) { args -> extreme(args, "max") { a, b -> a > b } },
        BuiltinFunction("len", 1..1) { args ->
            when (val v = args[0]) {
                is ListValue -> IntValue(v.items.size.toLong())
                is StrValue -> IntValue(v.value.length.toLong())
                else -> throw ScriptError("len() liste veya metin ister")
            }
        },
        BuiltinFunction("range", 1..3) { args -> range(args) },
    ).associateBy { it.name }

    private fun extreme(args: List<Value>, name: String, better: (Double, Double) -> Boolean): Value {
        val items = if (args.size == 1 && args[0] is ListValue) (args[0] as ListValue).items else args
        if (items.isEmpty()) throw ScriptError("$name() boş liste alamaz")
        var best = items[0]
        for (v in items) if (better(toDouble(v), toDouble(best))) best = v
        return best
    }

    private fun range(args: List<Value>): Value {
        val ints = args.map { (it as? IntValue)?.value ?: throw ScriptError("range() tam sayı ister") }
        val (start, end, step) = when (ints.size) {
            1 -> Triple(0L, ints[0], 1L)
            2 -> Triple(ints[0], ints[1], 1L)
            else -> Triple(ints[0], ints[1], ints[2])
        }
        if (step == 0L) throw ScriptError("range() adımı 0 olamaz")
        val result = ArrayList<Value>()
        var i = start
        while (if (step > 0) i < end else i > end) {
            if (result.size >= MAX_RANGE) throw ScriptError("range() en fazla $MAX_RANGE eleman üretebilir")
            result += IntValue(i)
            i += step
        }
        return ListValue(result)
    }

    companion object {
        const val MAX_CALL_DEPTH = 64
        const val MAX_RANGE = 100_000

        /** Dil seviyesindeki yerleşik fonksiyonlar ve hangi özelliğe bağlı oldukları. */
        val LANGUAGE_BUILTIN_FEATURES: Map<String, Feature?> = mapOf(
            "print" to null,
            "str" to null,
            "abs" to Feature.OPERATORS,
            "min" to Feature.OPERATORS,
            "max" to Feature.OPERATORS,
            "len" to Feature.LISTS,
            "range" to Feature.FOR,
        )
    }
}
