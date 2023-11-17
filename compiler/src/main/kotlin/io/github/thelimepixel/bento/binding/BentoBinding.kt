package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType

class BentoBinding {
    fun bind(fileItems: List<FunctionRef.Node>, parentContext: BindingContext): Map<FunctionRef.Node, HIR.ScopeExpr?> {
        val map = fileItems.associateBy { it.name }
        val context = ChildBindingContext(parentContext, map)
        return fileItems.associateWith { context.bindFunction(it.node.toRedRoot()) }
    }

    private fun BC.bindFunction(node: RedNode): HIR.ScopeExpr? =
        node.firstChild(ST.ScopeExpr)?.let { bindScope(it) }


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
            ?.let { HIR.IdentExpr(node.ref, it) }
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