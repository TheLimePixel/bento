package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.BaseSets
import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

sealed interface Ref

sealed interface ParentRef

data class LocalRef(val node: HIR.Pattern) : Ref

data class MemberRef(val parent: ItemRef, val name: String, val type: ItemRef?)

enum class ItemType(val mutable: Boolean = false, val isType: Boolean = false) {
    Function,
    SingletonType(isType = true),
    RecordType(isType = true),
    Getter,
    Setter(mutable = true),
    Constant,
}

val ItemType.immutable: Boolean get() = !mutable

data class ItemRef(val parent: ParentRef, val name: String, val type: ItemType, val index: Int) : Ref, ParentRef {
    val rawName: String
        get() = name.toJVMIdent()

    override fun toString(): String = "$type($parent::$name)"
}

data class PackageASTInfo(
    val items: List<ItemRef>,
    val dataMap: Map<String, List<GreenNode>>,
    val importNode: GreenNode?
)

typealias PackageInfoMap = Map<SubpackageRef, PackageASTInfo>

fun GreenNode.collectItems(parent: PackageRef): PackageASTInfo {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val importNode = firstChild(ST.ImportStatement)?.node
    val items = childSequence()
        .map { it.node }
        .filter { it.type in BaseSets.definitions }
        .map {
            val name = it.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it)
            ItemRef(parent, name, itemTypeFrom(it), list.lastIndex)
        }
        .toList()

    return PackageASTInfo(items, dataMap, importNode)
}

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

fun String.toJVMIdent(): String {
    val builder = StringBuilder()
    for (c in this) {
        when (c) {
            '`' -> Unit
            '\\' -> builder.append("\\\\")
            '.' -> builder.append("\\d")
            ';' -> builder.append("\\s")
            '[' -> builder.append("\\b")
            '/' -> builder.append("\\f")
            '<' -> builder.append("\\l")
            else -> builder.append(c)
        }
    }
    return builder.toString()
}

fun PackageRef.subpackage(name: String) = SubpackageRef(this, name)

private tailrec fun pathOf(parent: PackageRef, path: Array<out String>, index: Int): PackageRef =
    if (index == path.size) parent else pathOf(SubpackageRef(parent, path[index]), path, index + 1)


fun pathOf(vararg path: String): PackageRef = pathOf(RootRef, path, 0)

fun itemTypeFrom(node: GreenNode) = when (node.type) {
    SyntaxType.GetDef -> ItemType.Getter
    SyntaxType.FunDef -> ItemType.Function
    SyntaxType.SetDef -> ItemType.Setter
    SyntaxType.LetDef -> ItemType.Constant
    SyntaxType.TypeDef -> when (val bodyType = node.lastChild(BaseSets.typeBodies)?.type) {
        null -> ItemType.SingletonType
        ST.Constructor -> ItemType.RecordType
        else -> error("Unexpected type body: $bodyType")
    }

    else -> error("Unsupported definition type: ${node.type}")
}