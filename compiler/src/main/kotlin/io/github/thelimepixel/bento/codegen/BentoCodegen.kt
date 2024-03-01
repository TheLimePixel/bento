package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.typing.PathType
import io.github.thelimepixel.bento.typing.THIR
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.math.max

private typealias JC = JVMBindingContext

class BentoCodegen {
    fun generate(
        file: ItemPath,
        items: List<ItemRef>,
        fileContext: JC,
        hirMap: Map<ItemRef, HIR.Def>,
        thirMap: Map<ItemRef, THIR>
    ): List<Pair<String, ByteArray>> {
        val classes = mutableListOf<Pair<String, ByteArray>>()

        val writer = ClassWriter(0)

        writer.visit(
            52,
            Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
            file.toJVMPath() + "Bt",
            null,
            "java/lang/Object",
            null,
        )

        writer.visitSource(file.rawName + ".bt", null)

        val constants = mutableListOf<ItemRef>()

        items.forEach { ref ->
            when (val def = hirMap[ref]!!) {
                is HIR.FunctionLikeDef ->
                    fileContext.genFunctionLikeDef(def, thirMap, ref, writer)

                is HIR.ConstantDef -> {
                    fileContext.genConstantGetter(thirMap, ref, writer)
                    constants.add(ref)
                }

                is HIR.TypeDef -> classes.add(ref.path.toClassname() to genSingletonType(ref))
            }
        }

        genStaticInitializer(writer, constants, fileContext, thirMap)

        writer.visitEnd()
        classes.add(file.toClassname() + "Bt" to writer.toByteArray())

        return classes
    }

    private fun refToName(ref: ItemPath?, builder: StringBuilder) {
        if (ref == null) return
        refToName(ref.parent, builder)
        builder.append(ref.rawName).append('.')
    }

    private fun ItemPath.toClassname(): String = StringBuilder()
        .also { refToName(this.parent, it) }
        .append(this.rawName)
        .toString()

    private fun genSingletonType(ref: ItemRef): ByteArray {
        val writer = ClassWriter(0)

        val `class` = ref.path.toJVMPath()
        val type = "L$`class`;"

        writer.visit(
            52,
            Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC,
            `class`,
            null,
            "java/lang/Object",
            null,
        )

        val ctor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null)
        ctor.visitVarInsn(Opcodes.ALOAD, 0)
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()

        writer.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC, "INSTANCE", type, null, null)

        val staticCtor = writer.visitMethod(Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE, "<clinit>", "()V", null, null)
        staticCtor.visitTypeInsn(Opcodes.NEW, `class`)
        staticCtor.visitInsn(Opcodes.DUP)
        staticCtor.visitMethodInsn(Opcodes.INVOKESPECIAL, `class`, "<init>", "()V", false)
        staticCtor.visitFieldInsn(Opcodes.PUTSTATIC, `class`, "INSTANCE", type)
        staticCtor.visitInsn(Opcodes.RETURN)
        staticCtor.visitMaxs(2, 0)
        staticCtor.visitEnd()

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun genStaticInitializer(
        writer: ClassWriter,
        constants: List<ItemRef>,
        fileContext: JC,
        thirMap: Map<ItemRef, THIR>
    ) {
        if (constants.isEmpty()) return

        val staticInitWriter = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE, "<clinit>", "()V", null,
            null
        )
        var maxStack = 0
        var locals = 0

        constants.forEach { ref ->
            val type = fileContext.jvmTypeOf(thirMap[ref]!!.type.accessType)
            val isVoid = type == "V"
            if (!isVoid)
                writer.visitField(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                    ref.rawName,
                    type,
                    null,
                    null
                )
            val thir = thirMap[ref]!!
            val info = jvmFunctionInfoOf(thir)
            maxStack = max(maxStack, info.maxStackSize)
            val localContext = LocalJVMBindingContext(fileContext, info.varIds.mapValues { it.value + locals })
            locals += info.varIds.size
            localContext.genExpr(thir, staticInitWriter, isVoid)
            if (!isVoid)
                staticInitWriter.visitFieldInsn(Opcodes.PUTSTATIC, ref.fileJVMPath, ref.rawName, type)
        }

        staticInitWriter.visitInsn(Opcodes.RETURN)
        staticInitWriter.visitMaxs(maxStack, locals)
        staticInitWriter.visitEnd()
    }

    private fun JC.genFunctionLikeDef(
        def: HIR.FunctionLikeDef,
        thirMap: Map<ItemRef, THIR>,
        ref: ItemRef,
        writer: ClassWriter,
    ) {
        val info = jvmFunctionInfoOf(def, thirMap[ref])
        val sig = signatureOf(ref)
        val methodContext = LocalJVMBindingContext(this, info.varIds)
        val methodVisitor = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
            sig.name,
            sig.descriptor,
            null,
            null
        )
        val isVoid = sig.descriptor.endsWith("V")
        methodContext.genExpr(thirMap[ref]!!, methodVisitor, isVoid)
        methodVisitor.visitInsn(if (isVoid) Opcodes.RETURN else Opcodes.ARETURN)
        methodVisitor.visitMaxs(info.maxStackSize, info.varIds.size)
        methodVisitor.visitEnd()
    }

    private fun JC.genConstantGetter(
        thirMap: Map<ItemRef, THIR>,
        ref: ItemRef,
        writer: ClassWriter,
    ) {
        val type = jvmTypeOf(thirMap[ref]!!.type.accessType)
        val methodVisitor = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
            ref.jvmName,
            "()$type",
            null,
            null
        )

        if (type == "V") {
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitMaxs(0, 0)
        } else {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, ref.fileJVMPath, ref.rawName, type)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitMaxs(1, 0)
        }

        methodVisitor.visitEnd()
    }

    private fun JC.genScopeExpr(
        node: THIR.ScopeExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        val statements = node.statements

        if (statements.isEmpty())
            return false

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, methodWriter, true) }
        return genExpr(statements.last(), methodWriter, ignoreOutput)
    }

    private fun MethodVisitor.visitMethodInsn(op: Int, signature: JVMSignature) {
        visitMethodInsn(op, signature.parent, signature.name, signature.descriptor, false)
    }

    private fun JC.genCallExpr(
        node: THIR.CallExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        node.args.forEach { genExpr(it, methodWriter, false) }
        methodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, signatureOf(node.fn))
        val returns = !node.type.isSingleton

        return when {
            returns && ignoreOutput -> {
                methodWriter.visitInsn(Opcodes.POP)
                false
            }

            !ignoreOutput && !returns ->
                genSingletonAccessExpr(node.type.accessType, methodWriter)

            else -> returns
        }
    }

    private fun JC.genAccessExpr(
        node: THIR.AccessExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        if (ignoreOutput) return false

        methodWriter.visitVarInsn(Opcodes.ALOAD, localId(node.binding))
        return true
    }

    private fun JC.genLetExpr(node: THIR.LetExpr, methodWriter: MethodVisitor): Boolean {
        val id = localId(node.local)
        genExpr(node.expr, methodWriter, false)
        methodWriter.visitVarInsn(Opcodes.ASTORE, id)
        return false
    }

    private fun genStringExpr(node: THIR.StringExpr, methodWriter: MethodVisitor, ignoreOutput: Boolean): Boolean {
        if (ignoreOutput)
            return false

        methodWriter.visitLdcInsn(node.rawContext)
        return true
    }

    private fun JC.genSingletonAccessExpr(
        nodeType: PathType,
        methodWriter: MethodVisitor,
    ): Boolean {
        val type = jvmClassOf(nodeType)
        methodWriter.visitFieldInsn(Opcodes.GETSTATIC, type, "INSTANCE", "L$type;")
        return true
    }

    private fun JC.genExpr(
        node: THIR,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean = when (node) {
        is THIR.StringExpr ->
            genStringExpr(node, methodWriter, ignoreOutput)

        is THIR.CallExpr ->
            genCallExpr(node, methodWriter, ignoreOutput)

        is THIR.ErrorExpr ->
            false

        is THIR.ScopeExpr ->
            genScopeExpr(node, methodWriter, ignoreOutput)

        is THIR.AccessExpr ->
            genAccessExpr(node, methodWriter, ignoreOutput)

        is THIR.LetExpr ->
            genLetExpr(node, methodWriter)

        is THIR.SingletonAccessExpr -> {
            if (!ignoreOutput) genSingletonAccessExpr(node.type.accessType, methodWriter)
            ignoreOutput
        }
    }
}