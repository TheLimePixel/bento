package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.BaseSets
import io.github.thelimepixel.bento.ast.GreenNode
import io.github.thelimepixel.bento.ast.SyntaxType

data class ASTInfo(
    val items: List<ItemRef>,
    val accessors: Map<String, Accessor>,
    val dataMap: Map<String, List<GreenNode>>,
    val importNode: GreenNode?
)

typealias InfoMap = Map<ParentRef, ASTInfo>

fun collectItems(node: GreenNode, parent: PackageRef, collection: MutableMap<ParentRef, ASTInfo>) {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val accessors = mutableMapOf<String, Accessor>()
    val importNode = node.firstChild(ST.ImportStatement)?.node
    val items = node.childSequence()
        .map { it.node }
        .filter { it.type in BaseSets.definitions }
        .map {
            val name = it.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it)
            val mutable = it.type == SyntaxType.LetDef && it.firstChild(ST.MutKeyword) != null
            val ref = ItemRef(parent, name, list.lastIndex,  itemTypeFrom(it), mutable)
            accessors[name] = Accessor(ref, AccessorType.Get)
            if (mutable) accessors[name + "_="] = Accessor(ref, AccessorType.Set)

            if (ref.type == ItemType.RecordType) {
                collection[ref] = collectFields(it.firstChild(ST.Constructor)!!.node, ref)
            }

            ref
        }
        .toList()

    collection[parent] = ASTInfo(items, accessors, dataMap, importNode)
}

private fun collectFields(node: GreenNode, parent: ItemRef): ASTInfo {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val accessors = mutableMapOf<String, Accessor>()
    val fields = node.childSequence()
        .filter { it.type == ST.Field }
        .map {
            val name = it.node.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it.node)
            val mutable = it.node.firstChild(ST.MutKeyword) != null
            val ref = ItemRef(parent, name,list.lastIndex, ItemType.Field, mutable)
            accessors[name] = Accessor(ref, AccessorType.Get)
            if (mutable) accessors[name + "_="] = Accessor(ref, AccessorType.Set)
            ref
        }
        .toList()

    return ASTInfo(fields, accessors, dataMap, null)
}

private fun itemTypeFrom(node: GreenNode) = when (node.type) {
    SyntaxType.FunDef ->
        if (node.lastChild(SyntaxType.ParamList) == null) ItemType.Getter
        else ItemType.Function

    SyntaxType.LetDef ->
        ItemType.StoredProperty

    SyntaxType.TypeDef -> when (val bodyType = node.lastChild(BaseSets.typeBodies)?.type) {
        null -> ItemType.SingletonType
        ST.Constructor -> ItemType.RecordType
        else -> error("Unexpected type body: $bodyType")
    }

    else -> error("Unsupported definition type: ${node.type}")
}