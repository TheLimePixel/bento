package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.codegen.capitalize

sealed interface ParentRef

sealed interface Ref

@JvmInline
value class LocalRef(private val index: Int) : Ref {
    override fun toString(): String = "\$$index"
}


enum class ItemType(val isType: Boolean = false) {
    Function,
    SingletonType(isType = true),
    RecordType(isType = true),
    Getter,
    StoredProperty,
    Field,
}

data class ItemRef(
    val parent: ParentRef,
    val name: String,
    val index: Int,
    val type: ItemType,
    val mutable: Boolean,
) : ParentRef, Ref {
    val rawName: String
        get() = name.toJVMIdent()

    override fun toString(): String = "$type($parent::$name)"
}

enum class AccessorType {
    Get,
    Set,
}

data class Accessor(val of: Ref, val type: AccessorType)

sealed interface PackageRef : ParentRef

data object RootRef : PackageRef

data class SubpackageRef(val parent: PackageRef, val name: String) : PackageRef {
    override fun toString(): String {
        val builder = StringBuilder()
        if (parent != RootRef) builder.append(parent.toString()).append("::")
        builder.append(name)
        return builder.toString()
    }

    val rawName: String
        get() = name.toJVMIdent()
}

fun String.toJVMIdent(): String =
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
    }


fun PackageRef.subpackage(name: String) = SubpackageRef(this, name)
fun packageAt(vararg path: String): PackageRef = packageAt(RootRef, path, 0)

private tailrec fun packageAt(parent: PackageRef, path: Array<out String>, index: Int): PackageRef =
    if (index == path.size) parent
    else packageAt(SubpackageRef(parent, path[index]), path, index + 1)


