package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType

class BentoBinding {
    fun bind(items: List<ItemRef>, nodes: ItemMap, parentContext: BindingContext): Map<ItemRef, HIR.Function> {
        val context = FileBindingContext(parentContext, items.associateBy { it.name })
        return items.associateWith {
            context.bindFunction(nodes[it.name]!!.first().node.toRedRoot())
        }
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(SyntaxType.Identifier)
        ?.let {
            val itemRef = refFor(it.content)
            if (itemRef is ItemRef && itemRef.type == ItemType.Type) HIR.TypeRef(it.ref, itemRef.path)
            else null
        }

    private fun BC.bindFunction(node: RedNode): HIR.Function {
        val params = node.firstChild(SyntaxType.ParamList)
            ?.childSequence()
            ?.filter { it.type == SyntaxType.Param }
            ?.map {
                val name = it.firstChild(SyntaxType.Identifier)?.content ?: ""
                val type = findAndBindTypeAnnotation(it)
                HIR.Param(it.ref, name, type)
            }
            ?.toList()
            ?: emptyList()

        val returnType = findAndBindTypeAnnotation(node)
        val context = FunctionBindingContext(this, params.associateBy({ it.name }, { LocalRef(it) }))
        val body = node.lastChild(SyntaxType.ScopeExpr)?.let { context.bindScope(it) }

        return HIR.Function(node.ref, params, returnType, body)
    }

    private fun BC.bindCall(node: RedNode): HIR.CallExpr {
        val on = node.firstChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.ref)
        val args = node
            .lastChild(ST.ArgList)
            ?.childSequence()
            ?.filter { it.type in BaseSets.expressions }
            ?.map { bindExpr(it) }
            ?.toList()
            ?: emptyList()

        return HIR.CallExpr(node.ref, on, args)
    }

    private fun BC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)

        ST.Identifier -> refFor(node.content)
            ?.let { HIR.IdentExpr(node.ref, it) }
            ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

        ST.CallExpr -> bindCall(node)

        ST.ScopeExpr -> bindScope(node)

        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun BC.bindScope(node: RedNode): HIR.ScopeExpr {
        val context = LocalBindingContext(this)
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { context.bindExpr(it) }
            .toList()

        return HIR.ScopeExpr(node.ref, statements)
    }
}