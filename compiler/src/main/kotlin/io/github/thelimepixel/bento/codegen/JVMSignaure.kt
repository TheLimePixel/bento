package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*

data class JVMSignature(val parent: String, val name: String, val descriptor: String)

private fun packageJVMPath(ref: PackageRef, builder: StringBuilder, pathDelim: String) {
    if (ref !is SubpackageRef) return
    packageJVMPath(ref.parent, builder, pathDelim)
    if (ref.parent != RootRef) builder.append(pathDelim)
    builder.append(ref.rawName)
}

private fun itemJVMPath(ref: ItemRef, builder: StringBuilder, pathDelim: String) {
    when (val parent = ref.parent) {
        is PackageRef -> {
            packageJVMPath(parent, builder, pathDelim)
            builder.append(pathDelim)
        }
        is ItemRef -> {
            itemJVMPath(parent, builder, pathDelim)
            builder.append("$")
        }
    }
    builder.append(ref.rawName)
}

fun ParentRef.toJVMPath(pathDelim: String = "/"): String = StringBuilder().also { builder ->
    when (this) {
        is PackageRef -> packageJVMPath(this, builder, pathDelim)
        is ItemRef -> itemJVMPath(this, builder, pathDelim)
    }
}.toString()