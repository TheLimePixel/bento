package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.*

data class ASTInfo(
    val items: List<ItemRef>,
    val accessors: Map<String, Accessor>,
    val importNode: GreenNode?
)

typealias InfoMap = Map<ParentRef, ASTInfo>

fun collectItems(node: GreenNode, parent: PackageRef, collection: MutableMap<ParentRef, ASTInfo>) {
    val accessors = mutableMapOf<String, Accessor>()
    val importNode = node.firstChild(ST.ImportStatement)?.node
    val items = node.toRedRoot().childSequence()
        .filter { it.type in BaseSets.definitions }
        .mapIndexed { index, it ->
            val name = it.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val ref = toRef(parent, name, index, it, collection)
            accessors[name] = Accessor(ref, AccessorType.Get)
            if (ref.mutable) accessors[name + "_="] = Accessor(ref, AccessorType.Set)

            ref
        }
        .toList()

    collection[parent] = ASTInfo(items, accessors, importNode)
}

private fun collectFields(node: RedNode, parent: ProductTypeRef): ASTInfo {
    val accessors = mutableMapOf<String, Accessor>()
    val fields = node.childSequence()
        .filter { it.type == ST.Field }
        .mapIndexed { index, child ->
            val name = child.firstChild(SyntaxType.Identifier)?.rawContent ?: ""
            val mutable = child.firstChild(ST.MutKeyword) != null
            val ref = FieldRef(parent, name, index, mutable)
            accessors[name] = Accessor(ref, AccessorType.Get)
            if (mutable) accessors[name + "_="] = Accessor(ref, AccessorType.Set)
            ref
        }
        .toList()

    return ASTInfo(fields, accessors, null)
}

private fun toRef(parent: ParentRef, name: String, index: Int, ast: RedNode, collection: MutableMap<ParentRef, ASTInfo>): ItemRef = when (ast.type) {
    SyntaxType.FunDef ->
        if (ast.lastChild(SyntaxType.ParamList) == null) GetterRef(parent, name, index, ast)
        else FunctionRef(parent, name, index, ast)

    SyntaxType.LetDef ->
        StoredPropertyRef(parent, name, index, ast.firstChild(ST.MutKeyword) != null, ast)

    SyntaxType.TypeDef -> {
        val body =  ast.lastChild(BaseSets.typeBodies)
        when (val bodyType = body?.type) {
            null -> SingletonTypeRef(parent, name, index, ast)
            ST.Constructor -> {
                val ref = ProductTypeRef(parent, name, index, ast)
                collection[ref] = collectFields(body, ref)
                ref
            }

            else -> error("Unexpected type body: $bodyType")
        }
    }

    else -> error("Unsupported definition type: ${ast.type}")
}