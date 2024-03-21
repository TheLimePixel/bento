package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.ItemType
import java.util.*

@JvmInline
value class JVMName internal constructor(val string: String) {
    override fun toString(): String = string
}


val ItemRef.jvmName: JVMName
    get() = when (type) {
        ItemType.Getter ->
            name.toJVMName().capitalize().let { JVMName("get${it.string}") }

        ItemType.SingletonType, ItemType.Function, ItemType.RecordType, ItemType.Field, ItemType.StoredProperty ->
            name.toJVMName()
    }

private fun String.capitalize() = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

fun JVMName.capitalize() = JVMName(this.string.capitalize())

fun String.toJVMName(): JVMName =
    this.asSequence().joinToString(separator = "") { c ->
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

val instanceName = JVMName("INSTANCE")