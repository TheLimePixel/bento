package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.RedNode

sealed interface NamedRef {
    val name: String
}

sealed interface ParentRef : NamedRef

sealed interface Ref

@JvmInline
value class LocalRef(private val index: Int) : Ref {
    override fun toString(): String = "\$$index"
}

sealed interface ItemRef : Ref {
    val parent: ParentRef
    val index: Int
    val ast: RedNode?
    val mutable: Boolean
}

sealed interface NamedItemRef : NamedRef, ItemRef

sealed interface TypeRef : ParentRef, NamedItemRef {
    override val mutable: Boolean
        get() = false
}

data class SingletonTypeRef(
    override val parent: ParentRef,
    override val name: String,
    override val index: Int,
    override val ast: RedNode?,
) : TypeRef {
    override fun toString(): String = "$parent::$name"
}

data class ProductTypeRef(
    override val parent: ParentRef,
    override val name: String,
    override val index: Int,
    override val ast: RedNode?,
) : TypeRef {
    override fun toString(): String = "$parent::$name"
}

data class StoredPropertyRef(
    override val parent: ParentRef,
    override val name: String,
    override val index: Int,
    override val mutable: Boolean,
    override val ast: RedNode,
) : NamedItemRef {
    override fun toString(): String = "$parent::$name"
}

data class FunctionRef(
    override val parent: ParentRef,
    override val name: String,
    override val index: Int,
    override val ast: RedNode?,
) : NamedItemRef {
    override val mutable: Boolean
        get() = false
    override fun toString(): String = "$parent::$name"
}

data class GetterRef(
    override val parent: ParentRef,
    override val name: String,
    override val index: Int,
    override val ast: RedNode,
) : NamedItemRef {
    override val mutable: Boolean
        get() = false
    override fun toString(): String = "$parent::$name"
}

data class FieldRef(
    override val parent: ProductTypeRef,
    override val name: String,
    override val index: Int,
    override val mutable: Boolean,
) : NamedItemRef {
    override val ast: RedNode?
        get() = null
    override fun toString(): String = "$parent.$name"
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


