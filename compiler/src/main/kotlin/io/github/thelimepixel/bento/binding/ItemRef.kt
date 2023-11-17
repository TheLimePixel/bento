package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

enum class ItemType {
    Function,
    Type
}

data class ItemRef(val pack: ItemPath, val name: String, val type: ItemType) {
    val path: ItemPath
        get() = pack.subpath(name)
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
    if (value.size != 1) null else ItemRef(itemPath, key, value.first().type)
}