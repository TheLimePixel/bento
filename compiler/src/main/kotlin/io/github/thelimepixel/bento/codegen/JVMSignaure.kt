package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.ItemPath

data class JVMSignature(val parent: String, val name: String, val descriptor: String)

private fun refToJVMPath(ref: ItemPath?, builder: StringBuilder) {
    if (ref == null) return
    refToJVMPath(ref.parent, builder)
    builder.append(ref.rawName).append("/")
}
fun ItemPath.toJVMPath(): String = StringBuilder()
    .also { refToJVMPath(this.parent, it) }
    .append(this.rawName)
    .toString()