package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.typing.THIR
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class BentoCodegen {
    fun generate(
        file: ItemPath,
        items: List<ItemRef>,
        context: JVMBindingContext,
        thirMap: Map<ItemRef, THIR>
    ): ByteArray {
        val writer = ClassWriter(1)
        writer.visit(
            52,
            Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
            file.toJVMPath(),
            null,
            "java/lang/Object",
            null
        )

        writer.visitSource(file.name + ".bt", null)

        items.forEach { ref ->
            val sig = context.signatureFor(ref)
            val methodVisitor = writer.visitMethod(
                Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
                sig.name,
                sig.descriptor,
                null,
                null
            )
            genExpr(thirMap[ref]!!, methodVisitor, context, true)
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitMaxs(0, 0)
            methodVisitor.visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun genScopeExpr(
        node: THIR.ScopeExpr,
        methodWriter: MethodVisitor,
        context: JVMBindingContext,
        ignoreOutput: Boolean
    ) {
        val statements = node.statements
        if (statements.isEmpty()) return

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, methodWriter, context, true) }
        genExpr(statements.last(), methodWriter, context, ignoreOutput)
    }

    private fun MethodVisitor.visitMethodInsn(op: Int, signature: JVMSignature) {
        visitMethodInsn(op, signature.parent, signature.name, signature.descriptor, false)
    }

    private fun genCallExpr(
        node: THIR.CallExpr,
        methodWriter: MethodVisitor,
        context: JVMBindingContext,
        ignoreOutput: Boolean
    ) {
        node.args.forEach { genExpr(it, methodWriter, context, false) }
        methodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, context.signatureFor(node.fn))
        if (ignoreOutput && node.type != BuiltinRefs.unit) methodWriter.visitInsn(Opcodes.POP)
    }

    private fun genExpr(
        node: THIR,
        methodWriter: MethodVisitor,
        context: JVMBindingContext,
        ignoreOutput: Boolean
    ) {
        when (node) {
            is THIR.StringExpr -> {
                methodWriter.visitLdcInsn(node.rawContext)
                if (ignoreOutput) methodWriter.visitInsn(Opcodes.POP)
            }

            is THIR.CallExpr -> genCallExpr(node, methodWriter, context, ignoreOutput)
            is THIR.ErrorExpr -> {}
            is THIR.ScopeExpr -> genScopeExpr(node, methodWriter, context, ignoreOutput)
        }
    }
}