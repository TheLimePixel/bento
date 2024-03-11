package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.BaseSets
import io.github.thelimepixel.bento.ast.GreenNode
import io.github.thelimepixel.bento.ast.SyntaxType

sealed interface Ref

sealed interface ParentRef

data class LocalRef(val node: HIR.Pattern) : Ref

enum class ItemType(val mutable: Boolean = false, val isType: Boolean = false) {
    Function,
    SingletonType(isType = true),
    RecordType(isType = true),
    Getter,
    Setter(mutable = true),
    Constant,
    Field,
}

val ItemType.immutable: Boolean get() = !mutable

data class ItemRef(val parent: ParentRef, val name: String, val type: ItemType, val index: Int) : Ref, ParentRef {
    val rawName: String
        get() = name.toJVMIdent()

    override fun toString(): String = "$type($parent::$name)"
}

data class ASTInfo(
    val items: List<ItemRef>,
    val dataMap: Map<String, List<GreenNode>>,
    val importNode: GreenNode?
)

typealias InfoMap = Map<ParentRef, ASTInfo>

fun collectItems(node: GreenNode, parent: PackageRef, collection: MutableMap<ParentRef, ASTInfo>) {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val importNode = node.firstChild(ST.ImportStatement)?.node
    val items = node.childSequence()
        .map { it.node }
        .filter { it.type in BaseSets.definitions }
        .map {
            val name = it.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it)
            val ref = ItemRef(parent, name, itemTypeFrom(it), list.lastIndex)
            if (ref.type == ItemType.RecordType) {
                collection[ref] = collectFields(it.firstChild(ST.Constructor)!!.node, ref)
            }
            ref
        }
        .toList()

    collection[parent] = ASTInfo(items, dataMap, importNode)
}

private fun collectFields(node: GreenNode, parent: ItemRef): ASTInfo {
    val dataMap = mutableMapOf<String, MutableList<GreenNode>>()
    val fields = node.childSequence()
        .filter { it.type == ST.Field }
        .map {
            val name = it.node.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val list = dataMap.computeIfAbsent(name) { mutableListOf() }
            list.add(it.node)
            ItemRef(parent, name, ItemType.Field, list.lastIndex)
        }
        .toList()

    return ASTInfo(fields, dataMap, null)
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
    }


fun PackageRef.subpackage(name: String) = SubpackageRef(this, name)

private tailrec fun pathOf(parent: PackageRef, path: Array<out String>, index: Int): PackageRef =
    if (index == path.size) parent
    else pathOf(SubpackageRef(parent, path[index]), path, index + 1)


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