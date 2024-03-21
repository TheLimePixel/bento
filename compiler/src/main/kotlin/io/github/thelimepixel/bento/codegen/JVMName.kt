package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.ParentRef
import java.util.*

@JvmInline
value class JVMName internal constructor(val string: String) {
    override fun toString(): String = string
}


val ParentRef.jvmName: JVMName
    get() = name.asSequence().joinToString(separator = "") { c ->
        when (c) {
            '`' -> ""
            '\\' -> "\\\\"
            '.' -> "\\d"
            ';' -> "\\s"
            '[' -> "\\b"
            '/' -> "\\f"
            '<' -> "\\l"
            else -> c.toString()
        }
    }.let {
        if (it.endsWith("_=")) "set" + it.dropLast(2).capitalize()
        else it
    }.let { JVMName(it) }

private fun String.capitalize() = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

fun JVMName.capitalize() = JVMName(this.string.capitalize())

val instanceName = JVMName("INSTANCE")