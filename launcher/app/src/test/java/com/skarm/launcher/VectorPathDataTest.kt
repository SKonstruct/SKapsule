package com.skarm.launcher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android's PathParser is stricter than SVG: it needs every command to have a whole number of
 * argument groups, and arc flags written as separate numbers. A drawable copied from an SVG
 * icon ("a5.7 5.7 0 00-.6-.2") compiles fine and then crashes the screen that inflates it, so
 * check every vector's path data at build time.
 */
class VectorPathDataTest {
    private val number = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")
    private val arity = mapOf(
        'm' to 2, 'l' to 2, 'h' to 1, 'v' to 1, 'c' to 6, 's' to 4, 'q' to 4, 't' to 2, 'a' to 7,
    )

    private fun problem(path: String): String? {
        var command: Char? = null
        var count = 0
        fun check(): String? {
            val n = arity[command?.lowercaseChar()] ?: return null
            return if (count % n != 0) "'$command' has $count numbers, not a multiple of $n" else null
        }
        for (token in Regex("[a-zA-Z]|${number.pattern}").findAll(path).map { it.value }) {
            if (token[0].isLetter()) {
                check()?.let { return it }
                command = token[0]
                count = 0
            } else {
                count++
            }
        }
        return check()
    }

    @Test
    fun everyVectorDrawablePathParsesOnAndroid() {
        val res = File("src/main/res")
        val failures = res.walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.parentFile?.name?.startsWith("drawable") == true }
            .flatMap { file ->
                Regex("android:pathData=\"([^\"]+)\"").findAll(file.readText()).mapNotNull { m ->
                    problem(m.groupValues[1])?.let { "${file.name}: $it" }
                }
            }
            .toList()
        assertTrue("Unparseable vector paths: $failures", failures.isEmpty())
    }
}
