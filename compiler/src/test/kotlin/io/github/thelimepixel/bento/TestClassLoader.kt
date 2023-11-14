package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.PackageRef

class TestClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    private fun refToName(ref: PackageRef?, builder: StringBuilder) {
        if (ref == null) return
        refToName(ref.parent, builder)
        builder.append(ref.name).append('.')
    }

    private fun PackageRef.toName(): String = StringBuilder()
        .also { refToName(this.parent, it) }
        .append(this.name)
        .append("Bt")
        .toString()

    fun load(file: PackageRef, bytecode: ByteArray): Class<*> {
        return super.defineClass(file.toName(), bytecode, 0, bytecode.size)
    }
}