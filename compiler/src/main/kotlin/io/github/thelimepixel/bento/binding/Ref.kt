package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.BaseSets
import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

sealed interface Ref

data class LocalRef(val node: HIR.Pattern): Ref

enum class ItemType(val mutable: Boolean = false) {
    Function,
    Type,
    Getter,
    Setter(mutable = true),
    Constant,
}

val ItemType.immutable: Boolean get() = !mutable

data class ItemRef(val path: ItemPath, val type: ItemType, val index: Int) : Ref {
    val name: String
        get() = path.name

    val rawName: String
        get() = path.rawName

    val parent: ItemPath
        get() = path.parent!!

    override fun toString(): String = "$type($path)"
}

data class PackageASTInfo(
    val items: List<ItemRef>,
    val dataMap: Map<String, List<GreenNode>>,
    val importNode: GreenNode?
)

fun GreenNode.collectItems(parentPath: ItemPath): PackageASTInfo {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val importNode = firstChild(ST.ImportStatement)?.node
    val items = childSequence()
        .map { it.node }
        .filter { it.type in BaseSets.definitions }
        .map {
            val name = it.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it)
            ItemRef(parentPath.subpath(name), itemTypeFrom(it.type), list.lastIndex)
        }
        .toList()

    return PackageASTInfo(items, dataMap, importNode)
}

fun itemTypeFrom(type: SyntaxType) = when (type) {
    SyntaxType.GetDef -> ItemType.Getter
    SyntaxType.FunDef -> ItemType.Function
    SyntaxType.SetDef -> ItemType.Setter
    SyntaxType.LetDef -> ItemType.Constant
    else -> error("Unsupported definition type")
}