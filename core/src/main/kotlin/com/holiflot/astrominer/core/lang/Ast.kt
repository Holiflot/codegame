package com.holiflot.astrominer.core.lang

class Program(val body: List<Stmt>)

sealed interface Stmt {
    val line: Int
}

data class ExprStmt(val expr: Expr, override val line: Int) : Stmt
data class Assign(val target: Expr, val value: Expr, override val line: Int) : Stmt
data class AugAssign(val target: Expr, val op: String, val value: Expr, override val line: Int) : Stmt
data class If(val condition: Expr, val body: List<Stmt>, val orElse: List<Stmt>, override val line: Int) : Stmt
data class While(val condition: Expr, val body: List<Stmt>, override val line: Int) : Stmt
data class For(val variable: String, val iterable: Expr, val body: List<Stmt>, override val line: Int) : Stmt
data class Def(val name: String, val params: List<String>, val body: List<Stmt>, override val line: Int) : Stmt
data class Return(val value: Expr?, override val line: Int) : Stmt
data class Global(val names: List<String>, override val line: Int) : Stmt
data class Break(override val line: Int) : Stmt
data class Continue(override val line: Int) : Stmt
data class Pass(override val line: Int) : Stmt

sealed interface Expr {
    val line: Int
}

data class IntLit(val value: Long, override val line: Int) : Expr
data class FloatLit(val value: Double, override val line: Int) : Expr
data class StrLit(val value: String, override val line: Int) : Expr
data class BoolLit(val value: Boolean, override val line: Int) : Expr
data class NoneLit(override val line: Int) : Expr
data class Name(val name: String, override val line: Int) : Expr
data class ListLit(val items: List<Expr>, override val line: Int) : Expr

/** op: "-", "+" veya "not" */
data class Unary(val op: String, val operand: Expr, override val line: Int) : Expr
data class Binary(val op: String, val left: Expr, val right: Expr, override val line: Int) : Expr

/** Zincirleme karşılaştırma: `a < b <= c` → left=a, ops=[<, <=], rights=[b, c] */
data class Compare(val left: Expr, val ops: List<String>, val rights: List<Expr>, override val line: Int) : Expr

/** op: "and" veya "or" (kısa devre) */
data class BoolOp(val op: String, val left: Expr, val right: Expr, override val line: Int) : Expr
data class Call(val callee: Expr, val args: List<Expr>, override val line: Int) : Expr
data class Attribute(val target: Expr, val name: String, override val line: Int) : Expr
data class Index(val target: Expr, val index: Expr, override val line: Int) : Expr
