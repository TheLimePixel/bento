package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType

class BentoBinding {
    fun bind(fileItems: List<FunctionRef.Node>, parentContext: BindingContext): Map<FunctionRef.Node, HIR.FunctionDef> {
        val map = fileItems.associateBy { it.name }
        val context = ChildBindingContext(parentContext, map)
        return fileItems.associateWith { context.bindFunction(it.node.toRedRoot()) }
    }

    private fun BC.bindFunction(node: RedNode): HIR.FunctionDef {
        val scopeNode = node.firstChild(ST.ScopeExpr)
        return HIR.FunctionDef(node.ref, bindScope(scopeNode))
    }

    private fun BC.bindCall(node: RedNode): HIR.CallExpr {
        val on = node.firstChild(BaseSets.expressions)
        val args = node
            .lastChild(ST.ArgList)
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { bindExpr(it) }
            .toList()

        return HIR.CallExpr(node.ref, bindExpr(on), args)
    }

    private fun BC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)

        ST.Identifier -> refFor(node.content)
            ?.let { HIR.IdentExpr(node.ref, it) }
            ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

        ST.CallExpr -> bindCall(node)

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