package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.BentoBinding
import io.github.thelimepixel.bento.binding.collectFunctions
import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.utils.EmptyIterator
import io.github.thelimepixel.bento.utils.ObjectFormatter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

class CodeTest {
    private val nodeFormatter = ASTFormatter()
    private val parsing = BentoParsing()
    private val binding = BentoBinding()
    private val objFormatter = ObjectFormatter()

    @TestFactory
    fun makeTest(): Iterator<DynamicTest> = iterator {
        val resource = CodeTest::class.java.classLoader
            .getResource("tests") ?: return@iterator
        File(resource.toURI()).listFiles()!!.forEach { handleTestDir(it) }
    }

    private suspend fun SequenceScope<DynamicTest>.handleTestDir(dir: File) =
        withContentOf(dir, "src/main.bt") { code ->
            test(dir, code, "Parse", ::parse)
            test(dir, code, "Bind", ::bind)
        }

    private inline fun withContentOf(dir: File, name: String, fn: (content: String) -> Unit) {
        val file = File(dir, name)
        if (file.canRead()) fn(file.readText())
    }

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        code: String,
        type: String,
        function: (code: String) -> String
    ) = withContentOf(dir, type.lowercase() + ".txt") { expected ->
        yield(dynamicTest("$dir: $type") { assertEquals(function(code), expected) })
    }

    private fun parse(code: String): String {
        val node = parsing.parseFIle(code)
        return nodeFormatter.format(node)
    }

    private fun bind(code: String): String {
        val node = parsing.parseFIle(code)
        val items = node.collectFunctions()
        val hirMap = binding.bind(items)

        return items.joinToString(separator = "\n") {
            val hir = hirMap[it]!!
            objFormatter.format(hir)
        }
    }
}