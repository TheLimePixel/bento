package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.codegen.*
import io.github.thelimepixel.bento.errors.ErrorType
import io.github.thelimepixel.bento.errors.collectErrors
import io.github.thelimepixel.bento.parsing.*
import io.github.thelimepixel.bento.typing.*
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.Formatter
import io.github.thelimepixel.bento.utils.ObjectFormatter
import io.github.thelimepixel.bento.utils.Spanned
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

class SourceTests {
    private val nodeFormatter: Formatter<GreenNode> = ASTFormatter()
    private val parsing = BentoParsing()
    private val binding = BentoBinding()
    private val objFormatter: Formatter<Any?> = ObjectFormatter()
    private val topBindingContext: BindingContext = FileBindingContext(null, BuiltinRefs.map)
    private val topTypingContext: TypingContext = TopLevelTypingContext()
    private val topJVMBindingContext: JVMBindingContext = TopLevelJVMBindingContext(
        printlnFilePath = pathOf("io", "github", "thelimepixel", "bento", "RunFunctionsKt"),
        printlnName = "fakePrintln",
        stringJVMType = "Ljava/lang/String;",
        unitJVMType = "V",
        nothingJVMType = "V",
        topTypingContext
    )
    private val typing = BentoTypechecking()
    private val bentoCodegen = BentoCodegen()
    private val bytecodeFormatter: Formatter<ByteArray> = BytecodeFormatter()
    private val classLoader = TestClassLoader(this::class.java.classLoader)
    private val itemPadding = "======="

    @TestFactory
    fun generate(): Iterator<DynamicTest> = iterator {
        val resource = SourceTests::class.java.classLoader.getResource("tests") ?: return@iterator
        File(resource.toURI()).listFiles()!!.forEach { handleTestDir(it) }
    }

    private suspend fun SequenceScope<DynamicTest>.handleTestDir(dir: File) =
        withContentOf(dir, "src/main.bt") { code ->
            val node = parsing.parseFIle(code)
            test(dir, code, "Parse") { formatAST(node) }

            val fileRef = pathOf(dir.name, "main")
            val itemMap = node.collectItems()
            val itemRefs = collectRefs(fileRef, itemMap)
            val hirMap = binding.bind(itemRefs, itemMap, topBindingContext)
            test(dir, code, "Bind") { formatItemTrees(hirMap) }

            val typingContext = FileTypingContext(topTypingContext, hirMap.mapValues { (_, value) -> value.type() })

            val thirMap = hirMap.mapValues { (_, node) ->
                typing.type(node, typingContext) ?: THIRError.Propagation.at(ASTRef(SyntaxType.File, 0..0))
            }
            test(dir, code, "Typecheck") { formatItemTrees(thirMap) }

            val jvmBindingContext = FileJVMBindingContext(topJVMBindingContext, typingContext)
            val bytecode = bentoCodegen.generate(fileRef, itemRefs, jvmBindingContext, hirMap, thirMap)
            test(dir, code, "Codegen") { bytecodeFormatter.format(bytecode) }

            test(dir, code, "Output") { invokeBytecode(fileRef, bytecode) }
        }

    private fun invokeBytecode(fileRef: ItemPath, bytecode: ByteArray): String {
        val clazz = classLoader.load(fileRef, bytecode)
        clazz.getDeclaredMethod("main").invoke(null)
        return printBuffer.toString().also { printBuffer.clear() }
    }

    private fun <Node, Err> formatItemTrees(hirMap: Map<ItemRef, Node>)
            where Err : ErrorType, Node : Spanned, Node : CodeTree<Node, Err> =
        hirMap.asSequence().joinToString("\n") { (key, value) ->
            val errors = collectErrors(value)
            "$itemPadding ${key.path} $itemPadding\n${objFormatter.format(value) + errors.joinToString("\n", "\n")}"
        }

    private fun formatAST(node: GreenNode): String {
        val ast = nodeFormatter.format(node)
        val errors = collectErrors(node.toRedRoot())
        return ast + errors.joinToString("\n", "\n")
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