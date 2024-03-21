package io.github.thelimepixel.bento.codegen

import org.objectweb.asm.Opcodes
import org.objectweb.asm.ClassWriter as ASMClassWriter



class ClassWriter(`class`: JVMClass, visibility: JVMVisibility = JVMVisibility.PUBLIC) {
    @PublishedApi
    internal val visitor = ASMClassWriter(0).apply {
        visit(
            52,
            Opcodes.ACC_FINAL + visibility.code,
            `class`.string,
            null,
            "java/lang/Object",
            null
        )
    }

    @PublishedApi
    internal inline fun method(
        visibility: JVMVisibility,
        access: JVMAccess,
        name: String,
        descriptor: String,
        crossinline buildFn: (MethodWriter) -> Unit
    ) {
        val visitor = this.visitor.visitMethod(
            visibility.code + access.code,
            name,
            descriptor,
            null,
            null
        )
        val writer = MethodWriter(visitor, access)
        buildFn(writer)
        visitor.visitMaxs(writer.maxStack, writer.maxLocals)
        visitor.visitEnd()
    }

    inline fun virtualMethod(
        name: JVMName,
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.VIRTUAL, name.string, "$descriptor", buildFn)

    inline fun staticMethod(
        name: JVMName,
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.STATIC,name.string, "$descriptor", buildFn)

    inline fun virtualGetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.VIRTUAL,"get${name.capitalize()}", "()$type", buildFn)

    inline fun staticGetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.STATIC,"get${name.capitalize()}", "()$type", buildFn)

    inline fun virtualSetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.VIRTUAL,"set${name.capitalize()}", "($type)V", buildFn)

    inline fun staticSetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.STATIC,"set${name.capitalize()}", "($type)V", buildFn)

    inline fun constructor(
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC,
        crossinline buildFn: (MethodWriter) -> Unit
    ) = method(visibility, JVMAccess.VIRTUAL,"<init>", "$descriptor", buildFn)

    inline fun staticConstructor(crossinline buildFn: (MethodWriter) -> Unit) =
        method(JVMVisibility.PRIVATE, JVMAccess.STATIC,"<clinit>", "()V", buildFn)

    fun virtualField(
        name: JVMName,
        type: JVMType,
        mutable: Boolean,
        visibility: JVMVisibility = JVMVisibility.PRIVATE
    ) {
        visitor.visitField(
            visibility.code + if (mutable) 0 else Opcodes.ACC_FINAL,
            name.string,
            "$type",
            null,
            null
        ).visitEnd()
    }

    fun staticField(
        name: JVMName,
        type: JVMType,
        mutable: Boolean,
        visibility: JVMVisibility = JVMVisibility.PRIVATE
    ) {
        visitor.visitField(
            Opcodes.ACC_STATIC + visibility.code + if (mutable) 0 else Opcodes.ACC_FINAL,
            name.string,
            "$type",
            null,
            null
        ).visitEnd()
    }

    fun finish(): ByteArray {
        visitor.visitEnd()
        return visitor.toByteArray()
    }
}