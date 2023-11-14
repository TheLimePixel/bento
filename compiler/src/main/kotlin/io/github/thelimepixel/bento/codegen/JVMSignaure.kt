package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.PackageRef

data class JVMSignature(val parent: String, val name: String, val descriptor: String)

private fun refToJVMPath(ref: PackageRef?, builder: StringBuilder) {
    if (ref == null) return
    refToJVMPath(ref.parent, builder)
    builder.append(ref.name).append("/")
}
fun PackageRef.toJVMPath(): String = StringBuilder()
    .also { refToJVMPath(this.parent, it) }
    .append(this.name)
    .toString()