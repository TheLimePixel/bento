package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.codegen.*
import io.github.thelimepixel.bento.parsing.ASTFormatter
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.typing.BentoTypechecking
import io.github.thelimepixel.bento.typing.TopLevelTypingContext
import io.github.thelimepixel.bento.typing.TypingContext
import io.github.thelimepixel.bento.utils.Formatter
import io.github.thelimepixel.bento.utils.ObjectFormatter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
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
            test(dir, code, "Parse", ::parse)
            test(dir, code, "Bind", ::bind)
            test(dir, code, "Typecheck", ::typeCheck)
            test(dir, code, "Codegen", ::codeGen)
            test(dir, code, "Output", ::output)
        }

    private inline fun withContentOf(dir: File, name: String, fn: (content: String) -> Unit) {
        val file = File(dir, name)
        if (file.canRead()) fn(file.readText().trimIndent().trim())
    }

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        code: String,
        type: String,
        function: (code: String, groupName: String) -> String
    ) = withContentOf(dir, type.lowercase() + ".txt") { expected ->
        yield(dynamicTest("${dir.name}: $type") { assertEquals(function(code, dir.name).trim(), expected) })
    }

    private fun parse(code: String, module: String): String {
        val node = parsing.parseFIle(code)
        return nodeFormatter.format(node)
    }

    private fun bind(code: String, module: String): String {
        val node = parsing.parseFIle(code)
        val items = node.collectFunctions(packageRefTo(module, "main"))
        val hirMap = binding.bind(items, bindingContext)

        return objFormatter.format(hirMap)
    }

    private fun typeCheck(code: String, module: String): String {
        val node = parsing.parseFIle(code)
        val items = node.collectFunctions(packageRefTo(module, "main"))
        val hirMap = binding.bind(items, bindingContext)
        val thirMap = hirMap.mapValues { typing.type(it.value.scope, typingContext) }

        return objFormatter.format(thirMap)
    }

    private fun codeGen(code: String, module: String): String {
        val node = parsing.parseFIle(code)
        val fileRef = packageRefTo(module, "main")
        val items = node.collectFunctions(fileRef)
        val hirMap = binding.bind(items, bindingContext)
        val thirMap = hirMap.mapValues { typing.type(it.value.scope, typingContext) }
        val bytecode = bentoCodegen.generate(fileRef, items, jvmBindingContext, thirMap)

        return bytecodeFormatter.format(bytecode)
    }

    private fun output(code: String, module: String): String {
        val node = parsing.parseFIle(code)
        val fileRef = packageRefTo(module, "main")
        val items = node.collectFunctions(fileRef)
        val hirMap = binding.bind(items, bindingContext)
        val thirMap = hirMap.mapValues { typing.type(it.value.scope, typingContext) }
        val bytecode = bentoCodegen.generate(fileRef, items, jvmBindingContext, thirMap)
        val clazz = classLoader.load(fileRef, bytecode)
        clazz.getDeclaredMethod("main").invoke(null)

        return printBuffer.toString().also { printBuffer.clear() }
    }
}