package io.github.thelimepixel.bento.codegen

import org.objectweb.asm.Opcodes

enum class JVMVisibility(@PublishedApi internal val code: Int) {
    PUBLIC(Opcodes.ACC_PUBLIC),
    PACKAGE(0),
    PRIVATE(Opcodes.ACC_PRIVATE)
}

enum class JVMAccess(@PublishedApi internal val code: Int) {
    VIRTUAL(0),
    STATIC(Opcodes.ACC_STATIC)
}