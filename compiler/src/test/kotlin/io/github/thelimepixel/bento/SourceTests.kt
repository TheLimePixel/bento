package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.codegen.*
import io.github.thelimepixel.bento.errors.collectErrors
import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.toRedRoot
import io.github.thelimepixel.bento.typing.BentoTypechecking
import io.github.thelimepixel.bento.typing.TopLevelTypingContext
import io.github.thelimepixel.bento.typing.TypingContext
import io.github.thelimepixel.bento.utils.Formatter
import io.github.thelimepixel.bento.utils.ObjectFormatter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

class SourceTests {
    private val nodeFormatter: Formatter<GreenNode> = ASTFormatter()
    private val parsing = BentoParsing()
    private val binding = BentoBinding()
    private val objFormatter: Formatter<Any> = ObjectFormatter()
    private val bindingContext: BindingContext = TopLevelBindingContext()
    private val typing = BentoTypechecking()
    private val typingContext: TypingContext = TopLevelTypingContext()
    private val jvmBindingContext: JVMBindingContext = TopLevelJVMBindingContext(
        JVMSignature("io/github/thelimepixel/bento/RunFunctionsKt", "println", "(Ljava/lang/String;)V")
    )
    private val bentoCodegen = BentoCodegen()
    private val bytecodeFormatter: Formatter<ByteArray> = BytecodeFormatter()
    private val classLoader = TestClassLoader(this::class.java.classLoader)

    @TestFactory
    fun generate(): Iterator<DynamicTest> = iterator {
        val resource = SourceTests::class.java.classLoader
            .getResource("tests") ?: return@iterator
        File(resource.toURI()).listFiles()!!.forEach { handleTestDir(it) }
    }

    private suspend fun SequenceScope<DynamicTest>.handleTestDir(dir: File) =
        withContentOf(dir, "src/main.bt") { code ->
            val node = parsing.parseFIle(code)
            test(dir, code, "Parse") {
                val ast = nodeFormatter.format(node)
                val errors = collectErrors(node.toRedRoot())
                ast + errors.joinToString("\n", "\n")
            }

            val fileRef = pathOf(dir.name, "main")
            val items = node.collectFunctions(fileRef)
            val hirMap = binding.bind(items, bindingContext)
            test(dir, code, "Bind") {
                hirMap.asSequence().joinToString("\n") { (key, value) ->
                    "${key.path}:\n${
                        value?.let { scope ->
                            val errors = collectErrors(scope)
                            objFormatter.format(scope) + errors.joinToString("\n", "\n")
                        }
                    }"
                }
            }

            val thirMap = hirMap.asSequence().mapNotNull { (key, value) ->
                value?.let { scope -> key to typing.type(scope, typingContext) }
            }.toMap()
            test(dir, code, "Typecheck") {
                thirMap.asSequence().joinToString("\n") { (key, value) ->
                    val errors = collectErrors(value)
                    "${key.path}:\n${objFormatter.format(value)}${errors.joinToString("\n", "\n")}"
                }
            }

            val bytecode = bentoCodegen.generate(fileRef, items, jvmBindingContext, thirMap)
            test(dir, code, "Codegen") { bytecodeFormatter.format(bytecode) }

            test(dir, code, "Output") {
                val clazz = classLoader.load(fileRef, bytecode)
                clazz.getDeclaredMethod("main").invoke(null)
                printBuffer.toString().also { printBuffer.clear() }
            }
        }

    private inline fun withContentOf(dir: File, name: String, fn: (content: String) -> Unit) {
        val file = File(dir, name)
        if (file.canRead()) fn(file.readText().trimIndent().trim())
    }

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        code: String,
        type: String,
        function: (code: String) -> String
    ) = withContentOf(dir, type.lowercase() + ".txt") { expected ->
        yield(dynamicTest("${dir.name}: $type") { assertEquals(expected, function(code).trimIndent().trim()) })
    }
}