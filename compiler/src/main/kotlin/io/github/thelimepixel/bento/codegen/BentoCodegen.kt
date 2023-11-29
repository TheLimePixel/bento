package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.THIR
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class BentoCodegen {
    fun generate(
        file: ItemPath,
        items: List<ItemRef>,
        fileContext: JVMBindingContext,
        hirMap: Map<ItemRef, HIR.Function>,
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

        writer.visitSource(file.name + ".bt", null)

        items.forEach { ref ->
            val sig = fileContext.signatureOf(ref)
            val info = jvmFunctionInfoOf(hirMap[ref]!!, thirMap[ref])
            val methodContext = LocalJVMBindingContext(fileContext, info.varIds)
            val methodVisitor = writer.visitMethod(
                Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
                sig.name,
                sig.descriptor,
                null,
                null
            )
            val isVoid = sig.descriptor.endsWith("V")
            genExpr(thirMap[ref]!!, methodVisitor, methodContext, isVoid)
            methodVisitor.visitInsn(if (isVoid) Opcodes.RETURN else Opcodes.ARETURN)
            methodVisitor.visitMaxs(info.maxStackSize, info.varIds.size)
            methodVisitor.visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun genScopeExpr(
        node: THIR.ScopeExpr,
        methodWriter: MethodVisitor,
        context: JVMBindingContext
    ): Boolean {
        val statements = node.statements
        if (statements.isEmpty()) return false

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, methodWriter, context, true) }
        return genExpr(statements.last(), methodWriter, context, false)
    }

    private fun MethodVisitor.visitMethodInsn(op: Int, signature: JVMSignature) {
        visitMethodInsn(op, signature.parent, signature.name, signature.descriptor, false)
    }

    private fun genCallExpr(
        node: THIR.CallExpr,
        methodWriter: MethodVisitor,
        context: JVMBindingContext
    ): Boolean {
        node.args.forEach { genExpr(it, methodWriter, context, false) }
        methodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, context.signatureOf(node.fn))
        return node.type != BuiltinTypes.unit
    }

    private fun genAccessExpr(
        node: THIR.AccessExpr,
        methodWriter: MethodVisitor,
        context: JVMBindingContext
    ): Boolean {
        when (val binding = node.binding) {
            is LocalRef -> methodWriter.visitVarInsn(Opcodes.ALOAD, context.localId(binding))
            is ItemRef -> TODO("Unsupported operation")
        }
        return true
    }

    private fun genExpr(
        node: THIR,
        methodWriter: MethodVisitor,
        context: JVMBindingContext,
        ignoreOutput: Boolean
    ): Boolean {
        val returns = when (node) {
            is THIR.StringExpr -> {
                methodWriter.visitLdcInsn(node.rawContext)
                true
            }

            is THIR.CallExpr -> genCallExpr(node, methodWriter, context)
            is THIR.ErrorExpr -> false
            is THIR.ScopeExpr -> genScopeExpr(node, methodWriter, context)
            is THIR.AccessExpr -> genAccessExpr(node, methodWriter, context)
        }
        if (returns && ignoreOutput) methodWriter.visitInsn(Opcodes.POP)
        return returns && !ignoreOutput
    }
}