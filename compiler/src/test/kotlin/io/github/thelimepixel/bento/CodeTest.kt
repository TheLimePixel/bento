package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.BentoBinding
import io.github.thelimepixel.bento.binding.BindingContext
import io.github.thelimepixel.bento.binding.TopLevelBindingContext
import io.github.thelimepixel.bento.binding.collectFunctions
import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.typing.BentoTypechecking
import io.github.thelimepixel.bento.typing.TopLevelTypingContext
import io.github.thelimepixel.bento.typing.TypingContext
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
    private val bindingContext: BindingContext = TopLevelBindingContext()
    private val typing = BentoTypechecking()
    private val typingContext: TypingContext = TopLevelTypingContext()

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
            test(dir, code, "Typecheck", ::typeCheck)
        }

    private inline fun withContentOf(dir: File, name: String, fn: (content: String) -> Unit) {
        val file = File(dir, name)
        if (file.canRead()) fn(file.readText().trimIndent())
    }

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        code: String,
        type: String,
        function: (code: String) -> String
    ) = withContentOf(dir, type.lowercase() + ".txt") { expected ->
        yield(dynamicTest("${dir.name}: $type") { assertEquals(function(code), expected) })
    }

    private fun parse(code: String): String {
        val node = parsing.parseFIle(code)
        return nodeFormatter.format(node)
    }

    private fun bind(code: String): String {
        val node = parsing.parseFIle(code)
        val items = node.collectFunctions()
        val hirMap = binding.bind(items, bindingContext)

        return objFormatter.format(hirMap)
    }

    private fun typeCheck(code: String): String {
        val node = parsing.parseFIle(code)
        val items = node.collectFunctions()
        val hirMap = binding.bind(items, bindingContext)
        val thirMap = hirMap.mapValues { typing.type(it.value.scope, typingContext) }

        return objFormatter.format(thirMap)
    }
}