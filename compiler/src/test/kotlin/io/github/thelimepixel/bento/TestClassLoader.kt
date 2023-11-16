package io.github.thelimepixel.bento

import io.github.thelimepixel.bento.binding.ItemPath

class TestClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    private fun refToName(ref: ItemPath?, builder: StringBuilder) {
        if (ref == null) return
        refToName(ref.parent, builder)
        builder.append(ref.name).append('.')
    }

    private fun ItemPath.toName(): String = StringBuilder()
        .also { refToName(this.parent, it) }
        .append(this.name)
        .append("Bt")
        .toString()

    fun load(file: ItemPath, bytecode: ByteArray): Class<*> {
        return super.defineClass(file.toName(), bytecode, 0, bytecode.size)
    }
}