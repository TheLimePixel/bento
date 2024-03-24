package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.ast.*
import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.codegen.*
import io.github.thelimepixel.bento.driver.CompilationInstance
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
    private val objFormatter: Formatter<Any> = ObjectFormatter()
    private val bytecodeFormatter: Formatter<ByteArray> = BytecodeFormatter()
    private val nodeFormatter: Formatter<GreenNode> = ASTFormatter()
    private val classLoader = TestClassLoader(this::class.java.classLoader)
    private val itemPadding = "======="

    private fun createInstance(): CompilationInstance {
        val typingContext = TopLevelTypingContext()
        val javaPackage = packageAt("java", "lang")
        val kotlinPackage = packageAt("kotlin")
        val printlnRef = ItemRef(
            ItemRef(
                packageAt("io", "github", "thelimepixel", "bento"),
                "RunFunctionsKt",
                9,
                ItemType.RecordType,
                false
            ),
            "fakePrintln",
            0,
            ItemType.Function, false
        )

        return CompilationInstance(
            PackageTree(),
            parsing = BentoParsing(),
            binding = BentoBinding(),
            topBindingContext = ParentBindingContext(
                null,
                RootRef,
                BuiltinRefs.map,
                emptyMap(),
                emptySet(),
            ),
            topTypingContext = typingContext,
            topJVMBindingContext = TopLevelJVMBindingContext(
                printlnRef = printlnRef,
                stringJVMType = ItemRef(javaPackage, "String", 0, ItemType.RecordType, false),
                unitJVMType = ItemRef(kotlinPackage, "Unit", 0, ItemType.SingletonType, false),
                nothingJVMType = ItemRef(kotlinPackage, "Nothing", 0, ItemType.RecordType, false),
                typingContext,
            ),
            typing = BentoTypechecking(),
            bentoCodegen = BentoCodegen(),
        )
    }

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

    private suspend fun SequenceScope<DynamicTest>.handleTestDir(dir: File): Unit = with(createInstance()) {
        val sources = mutableMapOf<SubpackageRef, String>()
        val rootPath = SubpackageRef(RootRef, dir.name)

        File(dir, "src").listFiles()?.forEach {
            traversePackageFiles(SubpackageRef(rootPath, it.nameWithoutExtension), it, packageTree, sources)
        } ?: return

        val astMap: InfoMap = buildMap {
            sources.forEach { (path, code) ->
                val node = parsing.parseFile(code)
                test(dir, "Parse", path) { formatAST(node) }
                collectItems(node, path, this)
            }
        }

        val rootContext = RootBindingContext(topBindingContext, packageTree.root, astMap)

        val hirMap = sources.keys.flatMap { pack ->
            val fileInfo = astMap[pack] ?: return@flatMap emptySequence()
            val imports = binding.bindImport(fileInfo.importNode, rootContext)
            test(dir, "Imports", pack) { objFormatter.format(imports.toString()) }

            val bindings = binding.bind(pack, imports, rootContext)
            test(dir, "Bind", pack) { formatItemTrees(bindings) }

            bindings.asSequence() + fileInfo.items.asSequence().flatMap childMap@{ ref ->
                binding.bind(ref, imports, rootContext).asSequence()
            }
        }.associate { (key, value) -> key to value }

        val typingContext = FileTypingContext(
            topTypingContext,
            hirMap.mapValues { (ref, value) -> value.type(ref) },
            hirMap,
            astMap,
        )

        val thirMap = hirMap.mapNotNull { (ref, node) ->
            typing.type(node, typingContext)?.let { ref to it }
        }.toMap()

        sources.forEach { (pack, _) ->
            test(dir, "Typecheck", pack) {
                formatItemTrees(thirMap.filterKeys { ref ->
                    ref.parent == pack
                })
            }
        }

        val jvmBindingContext = FileJVMBindingContext(topJVMBindingContext, typingContext, hirMap)

        val classes = sources.keys.associateWith { pack ->
            val classes = bentoCodegen.generate(pack, astMap[pack]!!.items, jvmBindingContext, hirMap, thirMap)
            test(dir, "Codegen", pack) {
                classes.joinToString(separator = "\n") { bytecodeFormatter.format(it.second) }
            }
            classes.map { (name, clazz) -> classLoader.load(name, clazz) }.last()
        }

        classes.forEach { (path, `class`) ->
            test(dir, "Output", path) { invokeBytecode(`class`) }
        }
    }

    private fun invokeBytecode(`class`: Class<*>): String {
        try {
            `class`.getDeclaredMethod("main").invoke(null)
        } catch (e: Exception) {
            printBuffer.append(e.cause)
        }
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