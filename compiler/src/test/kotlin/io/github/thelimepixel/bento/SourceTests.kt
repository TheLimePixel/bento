package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.ast.ASTFormatter
import io.github.thelimepixel.bento.ast.GreenNode
import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.codegen.BentoCodegen
import io.github.thelimepixel.bento.codegen.BytecodeFormatter
import io.github.thelimepixel.bento.codegen.FileJVMBindingContext
import io.github.thelimepixel.bento.codegen.TopLevelJVMBindingContext
import io.github.thelimepixel.bento.driver.CompilationInstance
import io.github.thelimepixel.bento.parsing.BentoParsing
import io.github.thelimepixel.bento.parsing.Parse
import io.github.thelimepixel.bento.typing.BentoTypechecking
import io.github.thelimepixel.bento.typing.FileTypingContext
import io.github.thelimepixel.bento.typing.TopLevelTypingContext
import io.github.thelimepixel.bento.typing.type
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.Formatter
import io.github.thelimepixel.bento.utils.ObjectFormatter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

class SourceTests {
    private val objFormatter: Formatter<Any?> = ObjectFormatter()
    private val bytecodeFormatter: Formatter<ByteArray> = BytecodeFormatter()
    private val nodeFormatter: Formatter<GreenNode> = ASTFormatter()
    private val classLoader = TestClassLoader(this::class.java.classLoader)
    private val itemPadding = "======="

    private fun createInstance(): CompilationInstance {
        val typingContext = TopLevelTypingContext()
        val javaPackage = packageAt("java", "lang")
        val kotlinPackage = packageAt("kotlin")
        val printlnRef = FunctionRef(
            ProductTypeRef(
                packageAt("io", "github", "thelimepixel", "bento"),
                "RunFunctionsKt",
                9,
                null
            ),
            "fakePrintln",
            0,
            null
        )

        return CompilationInstance(
            PackageTree(),
            parsing = BentoParsing(),
            binding = BentoBinding(),
            topBindingContext = ParentBindingContext(
                null,
                RootRef,
                BuiltinRefs.map,
                emptySet(),
            ),
            topTypingContext = typingContext,
            topJVMBindingContext = TopLevelJVMBindingContext(
                printlnRef = printlnRef,
                stringJVMType = ProductTypeRef(javaPackage, "String", 0, null),
                unitJVMType = SingletonTypeRef(kotlinPackage, "Unit", 0, null),
                nothingJVMType = ProductTypeRef(kotlinPackage, "Nothing", 0, null),
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
            packageTree.refSequence().forEach { (ref, packageNode) ->
                val subpackages = packageNode.children.mapValues { ref.subpackage(it.key) }

                val parse = sources[ref]?.let { code -> parsing.parseFile(code) }
                test(dir, "Parse", ref) { formatAST(parse) }
                collectItems(parse?.node, ref, subpackages, this)
            }
        }

        val rootContext = RootBindingContext(topBindingContext, astMap)

        val hirMap = sources.keys.flatMap { pack ->
            val fileInfo = astMap[pack] ?: return@flatMap emptySequence()
            val imports = binding.bindImport(fileInfo.importNode, rootContext)
            test(dir, "Imports", pack) { objFormatter.format(imports.toString()) }

            val bindings = binding.bind(pack, imports, rootContext)
            test(dir, "Bind", pack) { formatItemTrees(bindings) }

            bindings.asSequence() + fileInfo.items.asSequence()
                .filterIsInstance<ParentRef>()
                .flatMap childMap@{ ref -> binding.bind(ref, imports, rootContext).asSequence() }
        }.associate { (key, value) -> key to value }

        val typingContext = FileTypingContext(
            topTypingContext,
            hirMap.mapValues { (ref, _) -> type(ref, hirMap) },
            hirMap,
            astMap,
        )

        val thirMap = hirMap.mapNotNull { (ref, node) ->
            node?.let { hir -> typing.type(hir, typingContext) }?.let { ref to it }
        }.toMap()

        sources.forEach { (pack, _) ->
            test(dir, "Typecheck", pack) {
                formatItemTrees(thirMap.filterKeys { ref -> ref.parent == pack }.mapValues { it.value.thir })
            }
        }

        val jvmBindingContext = FileJVMBindingContext(topJVMBindingContext, typingContext, thirMap)

        val classes = sources.keys.mapNotNull inner@ { pack ->
            val items = astMap[pack]?.items ?: return@inner null
            val classes = bentoCodegen.generate(pack, items, jvmBindingContext)
            test(dir, "Codegen", pack) {
                classes.joinToString(separator = "\n") { bytecodeFormatter.format(it.second) }
            }
            pack to (classes.map { (name, clazz) -> classLoader.load(name, clazz) }.lastOrNull() ?: return@inner null)
        }.toMap()

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

    private fun <Node> formatItemTrees(nodeMap: Map<ItemRef, Node?>) where Node : CodeTree<Node> =
        nodeMap.asSequence().joinToString("\n\n") { (key, value) ->
            "$itemPadding $key $itemPadding\n${objFormatter.format(value)}"
        }

    private fun formatAST(parse: Parse?): String {
        if (parse == null) return "null"
        val ast = nodeFormatter.format(parse.node)
        return ast + parse.errors.joinToString("\n", "\n")
    }

    private inline fun withContentOf(dir: File, name: String, fn: (content: String) -> Unit) {
        val file = File(dir, name)
        if (file.canRead()) fn(file.readText().trimIndent().trim())
    }

    private fun itemPathToFilePath(packageRef: SubpackageRef, builder: StringBuilder) {
        val parent = packageRef.parent
        if (parent is SubpackageRef) {
            itemPathToFilePath(parent, builder)
            builder.append(File.separatorChar)
        }
        builder.append(packageRef.name)
    }

    private val SubpackageRef.toFilePath: String
        get() = StringBuilder()
            .also { builder -> itemPathToFilePath(this, builder) }
            .toString()

    private suspend fun SequenceScope<DynamicTest>.test(
        dir: File,
        type: String,
        pack: PackageRef,
        function: () -> String
    ) {
        if (pack !is SubpackageRef) return
        val path = type.lowercase() + pack.toFilePath.removePrefix(dir.name) + ".txt"
        withContentOf(dir, path) { expected ->
            yield(dynamicTest("$pack: $type") { assertEquals(expected, function().trimIndent().trim()) })
        }
    }
}