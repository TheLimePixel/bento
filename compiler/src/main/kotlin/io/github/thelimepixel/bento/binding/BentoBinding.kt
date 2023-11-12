package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType

class BentoBinding {
    fun bind(fileItems: List<FunctionRef.Node>): Map<FunctionRef.Node, HirNode> {
        val map = fileItems.associateBy { it.name }
        val context = ChildContext(TopLevelContext, map)
        return fileItems.associateWith { context.bindFunction(it.node.toRedRoot()) }
    }

    private fun BC.bindFunction(node: RedNode): FunctionDef {
        val scopeNode = node.firstChild(ST.ScopeExpr)
        return FunctionDef(node.ref, bindScope(scopeNode))
    }

    private fun BC.bindCall(node: RedNode): CallExpr {
        val on = node.firstChild(BaseSets.expressions)
        val args = node
            .lastChild(ST.ArgList)
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { bindExpr(it) }
            .toList()

        return CallExpr(node.ref, bindExpr(on), args)
    }

    private fun BC.bindExpr(node: RedNode): Expr = when (node.type) {
        ST.StringLiteral -> StringExpr(node.ref)

        ST.Identifier -> refFor(node.content)
            ?.let { IdentExpr(node.ref, it) }
            ?: ErrorExpr(node.ref, ErrorExpr.Type.InvalidIdentifier)

        ST.CallExpr -> bindCall(node)
        else -> ErrorExpr(node.ref, ErrorExpr.Type.Unknown)
    }

    private fun BC.bindScope(node: RedNode): ScopeExpr {
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { bindExpr(it) }
            .toList()

        return ScopeExpr(node.ref, statements)
    }
}