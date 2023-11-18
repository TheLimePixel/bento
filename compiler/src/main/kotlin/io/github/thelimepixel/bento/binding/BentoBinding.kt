package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType

class BentoBinding {
    fun bind(items: List<ItemRef>, nodes: ItemMap, parentContext: BindingContext): Map<ItemRef, HIR> {
        val context = ChildBindingContext(parentContext, items.associateBy { it.name })
        return items.associateWith { context.bind(nodes[it.name]!!.first().node.toRedRoot()) }
    }
    private fun BC.bind(node: RedNode): HIR =
        node.firstChild(ST.ScopeExpr)?.let { bindScope(it) } ?: HIRError.Propagation.at(node.ref)

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

    private fun BC.bindExpr(node: RedNode): HIR = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)

        ST.Identifier -> refFor(node.content)
            ?.let { HIR.IdentExpr(node.ref, it.path) }
            ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

        ST.CallExpr -> bindCall(node)

        ST.ScopeExpr -> bindScope(node)

        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun BC.bindScope(node: RedNode): HIR.ScopeExpr {
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { bindExpr(it) }
            .toList()

        return HIR.ScopeExpr(node.ref, statements)
    }
}