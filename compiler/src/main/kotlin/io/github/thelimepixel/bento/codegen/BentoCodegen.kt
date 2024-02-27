package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.BuiltinTypes
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
    ): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(
            52,
            Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
            file.toJVMPath() + "Bt",
            null,
            "java/lang/Object",
            null
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
            }
        }

        genStaticInitializer(writer, constants, fileContext, thirMap)

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

        val staticInitWriter = writer.visitMethod(Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE, "<clinit>", "()V", null,
            null)
        var maxStack = 0
        var locals = 0

        constants.forEach { ref ->
            val type = fileContext.jvmTypeOf(thirMap[ref]!!.type)
            val isVoid = type == "V"
            if (!isVoid)
                writer.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, ref.rawName, type, null, null)
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
        val type = jvmTypeOf(thirMap[ref]!!.type)
        val methodVisitor = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
            ref.jvmName,
            "()${type}",
            null,
            null
        )

        if (type == "V") {
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitMaxs(0, 0)
        } else {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, ref.fileJVMPath, ref.rawName, type)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitMaxs(1, 0);
        }

        methodVisitor.visitEnd()
    }

    private fun JC.genScopeExpr(
        node: THIR.ScopeExpr,
        methodWriter: MethodVisitor,
    ): Boolean {
        val statements = node.statements
        if (statements.isEmpty()) return false

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, methodWriter, true) }
        return genExpr(statements.last(), methodWriter, false)
    }

    private fun MethodVisitor.visitMethodInsn(op: Int, signature: JVMSignature) {
        visitMethodInsn(op, signature.parent, signature.name, signature.descriptor, false)
    }

    private fun JC.genCallExpr(
        node: THIR.CallExpr,
        methodWriter: MethodVisitor,
    ): Boolean {
        node.args.forEach { genExpr(it, methodWriter, false) }
        methodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, signatureOf(node.fn))
        return node.type != BuiltinTypes.unit
    }

    private fun JC.genAccessExpr(
        node: THIR.AccessExpr,
        methodWriter: MethodVisitor,
    ): Boolean {
        when (val binding = node.binding) {
            is LocalRef -> methodWriter.visitVarInsn(Opcodes.ALOAD, localId(binding))
            is ItemRef -> TODO("Unsupported operation")
        }
        return true
    }

    private fun JC.genLetExpr(node: THIR.LetExpr, methodWriter: MethodVisitor): Boolean {
        val id = localId(node.local)
        genExpr(node.expr, methodWriter, false)
        methodWriter.visitVarInsn(Opcodes.ASTORE, id)
        return false
    }

    private fun JC.genStringExpr(node: THIR.StringExpr, methodWriter: MethodVisitor): Boolean {
        methodWriter.visitLdcInsn(node.rawContext)
        return true
    }

    private fun JC.genExpr(
        node: THIR,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean
    ): Boolean {
        val returns = when (node) {
            is THIR.StringExpr -> genStringExpr(node, methodWriter)
            is THIR.CallExpr -> genCallExpr(node, methodWriter)
            is THIR.ErrorExpr -> false
            is THIR.ScopeExpr -> genScopeExpr(node, methodWriter)
            is THIR.AccessExpr -> genAccessExpr(node, methodWriter)
            is THIR.LetExpr -> genLetExpr(node, methodWriter)
        }
        if (returns && ignoreOutput) methodWriter.visitInsn(Opcodes.POP)
        return returns && !ignoreOutput
    }
}