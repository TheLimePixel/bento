package io.github.thelimepixel.bento.codegen

import org.objectweb.asm.Opcodes
import org.objectweb.asm.ClassWriter as ASMClassWriter

enum class JVMVisibility(internal val code: Int) {
    PUBLIC(Opcodes.ACC_PUBLIC),
    PACKAGE(0),
    PRIVATE(Opcodes.ACC_PRIVATE)
}

class ClassWriter(`class`: JVMClass, visibility: JVMVisibility = JVMVisibility.PUBLIC) {
    private val visitor = ASMClassWriter(0).apply {
        visit(
            52,
            Opcodes.ACC_FINAL + visibility.code,
            `class`.string,
            null,
            "java/lang/Object",
            null
        )
    }

    fun virtualMethod(
        name: JVMName,
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            visibility.code,
            name.string,
            "$descriptor",
            null,
            null
        ),
        true
    )

    fun staticMethod(
        name: JVMName,
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            Opcodes.ACC_STATIC + visibility.code,
            name.string,
            "$descriptor",
            null,
            null
        ),
        false
    )

    fun virtualGetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            visibility.code,
            "get${name.capitalize()}",
            "()$type",
            null,
            null
        ),
        true
    )

    fun staticGetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            Opcodes.ACC_STATIC + visibility.code,
            "get${name.capitalize()}",
            "()$type",
            null,
            null
        ),
        false
    )

    fun virtualSetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            visibility.code,
            "set${name.capitalize()}",
            "($type)V",
            null,
            null
        ) ,
            true
    )

    fun staticSetter(
        name: JVMName,
        type: JVMType,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            Opcodes.ACC_STATIC + visibility.code,
            "set${name.capitalize()}",
            "($type)V",
            null,
            null
        ),
        false
    )

    fun constructor(
        descriptor: JVMDescriptor,
        visibility: JVMVisibility = JVMVisibility.PUBLIC
    ): MethodWriter = MethodWriter(
        visitor.visitMethod(
            visibility.code,
            "<init>",
            "$descriptor",
            null,
            null
        ),
        true
    )

    fun staticConstructor(): MethodWriter = MethodWriter(
        visitor.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE,
            "<clinit>",
            "()V",
            null,
            null
        ),
        false
    )

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