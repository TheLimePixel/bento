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
}

val ItemType.immutable: Boolean get() = !mutable

data class ItemRef(val path: ItemPath, val type: ItemType, val index: Int) : Ref {
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
    .filter { it.type in BaseSets.definitions }
    .groupBy(
        keySelector = { it.firstChild(SyntaxType.Identifier)?.content ?: "" },
        valueTransform = { ItemData(itemTypeFrom(it.type), it) }
    )

fun itemTypeFrom(type: SyntaxType) = when (type) {
    SyntaxType.GetDef -> ItemType.Getter
    SyntaxType.FunDef -> ItemType.Function
    SyntaxType.SetDef -> ItemType.Setter
    else -> error("Unsupported definition type")
}

fun collectRefs(itemPath: ItemPath, itemMap: ItemMap): List<ItemRef> = itemMap.flatMap { (key, value) ->
    value.asSequence().mapIndexed { index, itemData -> ItemRef(itemPath.subpath(key), itemData.type, index) }
}