package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*

@JvmInline
value class JVMClass(val string: String) {
    override fun toString(): String = string
}

fun ParentRef.asJVMClass(): JVMClass = JVMClass(toJVMPath("/") + if (this is PackageRef) "Bt" else "")
fun ParentRef.toJVMPath(pathDelim: String): String = StringBuilder().also { builder ->
    when (this) {
        is PackageRef -> packageJVMPath(this, builder, pathDelim)
        is NamedItemRef -> itemJVMPath(this, builder, pathDelim)
    }
}.toString()

private fun packageJVMPath(ref: PackageRef, builder: StringBuilder, pathDelim: String) {
    if (ref !is SubpackageRef) return
    packageJVMPath(ref.parent, builder, pathDelim)
    if (ref.parent != RootRef) builder.append(pathDelim)
    builder.append(ref.jvmName)
}

private fun itemJVMPath(ref: NamedItemRef, builder: StringBuilder, pathDelim: String) {
    when (val parent = ref.parent) {
        is PackageRef -> {
            packageJVMPath(parent, builder, pathDelim)
            builder.append(pathDelim)
        }

        is NamedItemRef -> {
            itemJVMPath(parent, builder, pathDelim)
            builder.append("$")
        }
    }
    builder.append(ref.jvmName)
}

data class JVMSignature(val parent: JVMClass, val name: JVMName, val descriptor: JVMDescriptor)
