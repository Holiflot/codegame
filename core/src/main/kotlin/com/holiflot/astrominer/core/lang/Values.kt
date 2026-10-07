package com.holiflot.astrominer.core.lang

sealed interface Value

object NoneValue : Value {
    override fun toString() = "None"
}

class BoolValue private constructor(val value: Boolean) : Value {
    override fun toString() = if (value) "True" else "False"

    companion object {
        val TRUE = BoolValue(true)
        val FALSE = BoolValue(false)
        fun of(value: Boolean) = if (value) TRUE else FALSE
    }
}

data class IntValue(val value: Long) : Value {
    override fun toString() = value.toString()
}

data class FloatValue(val value: Double) : Value {
    override fun toString(): String =
        if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) {
            "${value.toLong()}.0"
        } else {
            value.toString()
        }
}

data class StrValue(val value: String) : Value {
    override fun toString() = value
}

class ListValue(val items: MutableList<Value>) : Value {
    override fun toString() = items.joinToString(", ", "[", "]") { repr(it) }
}

/** Oyun sabitleri: North, Entities.Crystal, Items.Stone ... */
data class EnumValue(val type: String, val name: String) : Value {
    override fun toString() = if (type == "Direction") name else "$type.$name"
}

/** `Entities.Crystal` gibi erişimler için isim alanı. */
class NamespaceValue(val name: String, val members: Map<String, Value>) : Value {
    override fun toString() = name
}

class FunctionValue(val def: Def) : Value {
    override fun toString() = "<fonksiyon ${def.name}>"
}

class BuiltinFunction(
    val name: String,
    val arity: IntRange,
    val impl: suspend (List<Value>) -> Value,
) : Value {
    override fun toString() = "<komut $name>"
}

fun repr(value: Value): String = if (value is StrValue) "\"${value.value}\"" else value.toString()

fun typeName(value: Value): String = when (value) {
    NoneValue -> "None"
    is BoolValue -> "bool"
    is IntValue -> "int"
    is FloatValue -> "float"
    is StrValue -> "metin"
    is ListValue -> "liste"
    is EnumValue -> value.type
    is NamespaceValue -> value.name
    is FunctionValue, is BuiltinFunction -> "fonksiyon"
}

fun truthy(value: Value): Boolean = when (value) {
    NoneValue -> false
    is BoolValue -> value.value
    is IntValue -> value.value != 0L
    is FloatValue -> value.value != 0.0
    is StrValue -> value.value.isNotEmpty()
    is ListValue -> value.items.isNotEmpty()
    else -> true
}

fun valuesEqual(a: Value, b: Value): Boolean = when {
    a is IntValue && b is IntValue -> a.value == b.value
    (a is IntValue || a is FloatValue) && (b is IntValue || b is FloatValue) -> toDouble(a) == toDouble(b)
    a is ListValue && b is ListValue ->
        a.items.size == b.items.size && a.items.indices.all { valuesEqual(a.items[it], b.items[it]) }
    else -> a == b
}

fun toDouble(value: Value): Double = when (value) {
    is IntValue -> value.value.toDouble()
    is FloatValue -> value.value
    else -> throw ScriptError("Sayı bekleniyordu, ${typeName(value)} verildi")
}
