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
    private val memberCollection = BentoMemberCollection()
    private val objFormatter: Formatter<Any?> = ObjectFormatter()
    private val topBindingContext: BindingContext = PackageBindingContext(
        null,
        null,
        BuiltinRefs.map,
        emptyMap(),
        emptyMap(),
        emptySet()
    )
    private val topTypingContext: TypingContext = TopLevelTypingContext()
    private val topJVMBindingContext: JVMBindingContext = TopLevelJVMBindingContext(
        printlnFilePath = pathOf("io", "github", "thelimepixel", "bento", "RunFunctionsKt"),
        printlnName = "fakePrintln",
        stringJVMType = "java/lang/String",
        unitJVMType = "kotlin/Unit",
        nothingJVMType = "kotlin/Nothing",
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

    private fun traversePackageFiles(
        path: SubpackageRef,
        file: File,
        hierarchy: PackageTree,
        sources: MutableMap<SubpackageRef, String>
    ) {
        if (file.canRead()) {
            hierarchy.add(path)
            sources[path] = file.readText()
        } else {
            file.listFiles()?.forEach { child ->
                traversePackageFiles(path.subpackage(child.nameWithoutExtension), child, hierarchy, sources)
            }
        }
    }

    private suspend fun SequenceScope<DynamicTest>.handleTestDir(dir: File) {
        val hierarchy = PackageTree()
        val sources = mutableMapOf<SubpackageRef, String>()
        val rootPath = SubpackageRef(RootRef, dir.name)

        File(dir, "src").listFiles()?.forEach {
            traversePackageFiles(SubpackageRef(rootPath, it.nameWithoutExtension), it, hierarchy, sources)
        } ?: return

        val packageItems = sources.mapValues { (path, code) ->
            val node = parsing.parseFIle(code)
            test(dir, "Parse", path) { formatAST(node) }
            node.collectItems(path)
        }

        val rootContext = RootBindingContext(topBindingContext, hierarchy.root, packageItems)

        val hirMap = packageItems
            .flatMap { (path, fileInfo) ->
                val imports = binding.bindImport(fileInfo.importNode, rootContext)
                test(dir, "Imports", path) { objFormatter.format(imports.toString()) }

                val bindings = binding.bind(path, imports, rootContext)
                test(dir, "Bind", path) { formatItemTrees(bindings) }

                bindings.asSequence()
            }.associate { (key, value) -> key to value }

        val memberMap = hirMap.mapValues { (ref, hir) ->
            memberCollection.collectMembers(ref, hir)
        }

        val typingContext = FileTypingContext(
            topTypingContext,
            hirMap.mapValues { (ref, value) -> value.type(ref) },
            hirMap,
            memberMap
        )

        val thirMap = hirMap.mapValues { (_, node) ->
            typing.type(node, typingContext) ?: THIRError.Propagation.at(ASTRef(SyntaxType.File, 0..0))
        }

        sources.forEach { (pack, _) ->
            test(dir, "Typecheck", pack) {
                formatItemTrees(thirMap.filterKeys { ref ->
                    ref.parent == pack
                })
            }
        }

        val jvmBindingContext = FileJVMBindingContext(topJVMBindingContext, typingContext, hirMap)

        val classes = packageItems.mapValues { (path, fileInfo) ->
            val classes = bentoCodegen.generate(path, fileInfo.items, jvmBindingContext, hirMap, thirMap)
            test(dir, "Codegen", path) { classes.joinToString(separator = "\n") { bytecodeFormatter.format(it.second) } }
            classes.map { (name, clazz) -> classLoader.load(name, clazz) }.last()
        }

        classes.forEach { (path, `class`) ->
            test(dir, "Output", path) { invokeBytecode(`class`) }
        }
    }

    private fun invokeBytecode(`class`: Class<*>): String {
        `class`.getDeclaredMethod("main").invoke(null)
        return printBuffer.toString().also { printBuffer.clear() }
    }

    private fun <Node, Err> formatItemTrees(hirMap: Map<ItemRef, Node>)
            where Err : ErrorType, Node : Spanned, Node : CodeTree<Node, Err> =
        hirMap.asSequence().joinToString("\n") { (key, value) ->
            val errors = collectErrors(value)
            "$itemPadding $key $itemPadding\n${objFormatter.format(value) + errors.joinToString("\n", "\n")}"
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

    private fun itemPathToFilePath(packageRef: PackageRef, builder: StringBuilder) {
        if (packageRef !is SubpackageRef) return
        itemPathToFilePath(packageRef.parent, builder)
        builder.append(packageRef.name).append(File.separatorChar)
    }

    private val SubpackageRef.toFilePath: String
        get() = StringBuilder()
            .also { builder -> itemPathToFilePath(this.parent, builder) }
            .append(this.name)
            .toString()

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        type: String,
        pack: SubpackageRef,
        function: () -> String
    ) {
        val path = type.lowercase() + pack.toFilePath.removePrefix(dir.name) + ".txt"
        withContentOf(dir, path) { expected ->
            yield(dynamicTest("$pack: $type") { assertEquals(expected, function().trimIndent().trim()) })
        }
    }
}