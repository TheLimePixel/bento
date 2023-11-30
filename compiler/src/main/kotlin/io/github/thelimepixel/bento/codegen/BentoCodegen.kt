package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.THIR
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

private typealias JC = JVMBindingContext

class BentoCodegen {
    fun generate(
        file: ItemPath,
        items: List<ItemRef>,
        fileContext: JC,
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
            methodContext.genExpr(thirMap[ref]!!, methodVisitor, isVoid)
            methodVisitor.visitInsn(if (isVoid) Opcodes.RETURN else Opcodes.ARETURN)
            methodVisitor.visitMaxs(info.maxStackSize, info.varIds.size)
            methodVisitor.visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
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