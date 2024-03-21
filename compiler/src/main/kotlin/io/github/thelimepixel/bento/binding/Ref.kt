package io.github.thelimepixel.bento.binding

sealed interface ParentRef {
    val name: String
}

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
    override val name: String,
    val index: Int,
    val type: ItemType,
    val mutable: Boolean,
) : ParentRef, Ref {
    override fun toString(): String = "$type($parent::$name)"
}

enum class AccessorType {
    Get,
    Set,
}

data class Accessor(val of: Ref, val type: AccessorType)

sealed interface PackageRef : ParentRef

data object RootRef : PackageRef {
    override val name: String
        get() = ""
}

data class SubpackageRef(val parent: PackageRef, override val name: String) : PackageRef {
    override fun toString(): String {
        val builder = StringBuilder()
        if (parent != RootRef) builder.append(parent.toString()).append("::")
        builder.append(name)
        return builder.toString()
    }
}


fun PackageRef.subpackage(name: String) = SubpackageRef(this, name)
fun packageAt(vararg path: String): PackageRef = packageAt(RootRef, path, 0)

private tailrec fun packageAt(parent: PackageRef, path: Array<out String>, index: Int): PackageRef =
    if (index == path.size) parent
    else packageAt(SubpackageRef(parent, path[index]), path, index + 1)


