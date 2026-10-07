package com.holiflot.astrominer.core.lang

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InterpreterTest {
    private fun run(source: String): List<String> {
        val output = mutableListOf<String>()
        val host = object : InterpreterHost {
            override suspend fun onStatement(line: Int) {}
            override fun print(text: String) {
                output += text
            }
        }
        runBlocking { Interpreter(host).run(Parser.parse(source)) }
        return output
    }

    @Test
    fun arithmeticAndPrecedence() {
        assertEquals(listOf("7", "2.5", "2", "-1", "1024", "True"), run(
            """
            print(1 + 2 * 3)
            print(5 / 2)
            print(5 // 2)
            print(-7 % 3 - 3)
            print(2 ** 10)
            print(1 < 2 <= 2 and not False)
            """.trimIndent(),
        ))
    }

    @Test
    fun whileWithBreakAndContinue() {
        val out = run(
            """
            i = 0
            while True:
                i += 1
                if i == 2:
                    continue
                if i > 4:
                    break
                print(i)
            """.trimIndent(),
        )
        assertEquals(listOf("1", "3", "4"), out)
    }

    @Test
    fun elifChain() {
        val out = run(
            """
            for x in range(3):
                if x == 0:
                    print("sıfır")
                elif x == 1: print("bir")
                else:
                    print("çok")
            """.trimIndent(),
        )
        assertEquals(listOf("sıfır", "bir", "çok"), out)
    }

    @Test
    fun functionsScopesAndRecursion() {
        val out = run(
            """
            total = 0
            def fib(n):
                if n < 2:
                    return n
                return fib(n - 1) + fib(n - 2)
            def bump():
                global total
                total += 1
            def shadow():
                total = 100
            bump()
            bump()
            shadow()
            print(fib(10), total)
            """.trimIndent(),
        )
        assertEquals(listOf("55 2"), out)
    }

    @Test
    fun lists() {
        val out = run(
            """
            a = [3, 1, 2]
            a.append(5)
            a[0] = a.pop() + a[-1]
            print(a, len(a), 1 in a, max(a))
            """.trimIndent(),
        )
        assertEquals(listOf("[7, 1, 2] 3 True 7"), out)
    }

    @Test
    fun errorsCarryLineNumbers() {
        val e = assertFailsWith<ScriptError> { run("x = 1\nprint(y)") }
        assertEquals(2, e.line)
        val parse = assertFailsWith<ScriptError> { run("if True\n    pass") }
        assertEquals(1, parse.line)
        val indent = assertFailsWith<ScriptError> { run("if True:\n        pass\n    pass") }
        assertEquals(3, indent.line)
    }

    @Test
    fun breakOutsideLoopIsRejected() {
        assertFailsWith<ScriptError> { Parser.parse("break") }
        assertFailsWith<ScriptError> { Parser.parse("def f():\n    return\nreturn 1") }
    }

    @Test
    fun recursionDepthIsLimited() {
        val e = assertFailsWith<ScriptError> { run("def f():\n    f()\nf()") }
        assertTrue("fonksiyon" in e.message)
    }

    @Test
    fun featureCheckerReportsFirstUseOnly() {
        val program = Parser.parse("while True:\n    x = 1\n    y = 2\n    move(North)")
        val errors = FeatureChecker(setOf(Feature.WHILE), mapOf("move" to "Genişletme", "North" to "Genişletme"))
            .check(program)
        assertEquals(listOf(2, 4, 4), errors.map { it.line })
    }
}
