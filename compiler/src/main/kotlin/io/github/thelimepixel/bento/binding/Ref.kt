package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

sealed interface Ref

data class LocalRef(val node: HIR.Pattern): Ref

enum class ItemType {
    Function,
    Type
}

data class ItemRef(val path: ItemPath, val type: ItemType) : Ref {
    val name: String
        get() = path.name

    val parent: ItemPath
        get() = path.parent!!

    override fun toString(): String = "$type($path)"
}

data class ItemData(val type: ItemType, val node: GreenNode)

typealias ItemMap = Map<String, List<ItemData>>

fun GreenNode.collectItems(): ItemMap = childSequence()
    .map { it.node }
    .filter { it.type == SyntaxType.FunDef }
    .groupBy(
        keySelector = { it.firstChild(SyntaxType.Identifier)?.content ?: "" },
        valueTransform = { ItemData(ItemType.Function, it) }
    )

fun collectRefs(itemPath: ItemPath, itemMap: ItemMap): List<ItemRef> = itemMap.mapNotNull { (key, value) ->
    if (value.size != 1) null else ItemRef(itemPath.subpath(key), value.first().type)
}