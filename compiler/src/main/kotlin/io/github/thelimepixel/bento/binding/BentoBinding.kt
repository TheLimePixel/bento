package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType
private typealias LC = LocalBindingContext

class BentoBinding {
    fun bind(items: List<ItemRef>, nodes: ItemMap, parentContext: BindingContext): Map<ItemRef, HIR.FunctionLike> {
        val context = FileBindingContext(parentContext, items.associateBy { it.name })
        return items.associateWith {
            context.bindDefinition(nodes[it.name]!!.first().node.toRedRoot())
        }
    }

    private fun BC.bindDefinition(node: RedNode): HIR.FunctionLike = when (node.type) {
        ST.FunDef -> bindFunction(node)
        ST.GetDef -> bindGetter(node)
        else -> error("Unsupported definition type")
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(SyntaxType.Identifier)
        ?.let {
            val itemRef = refFor(it.content)
            if (itemRef is ItemRef && itemRef.type == ItemType.Type)
                HIR.TypeRef(it.ref, itemRef.path)
            else null
        }

    private fun findAndBindPattern(node: RedNode): HIR.Pattern =
        node.firstChild(BaseSets.patterns)?.let {
            when (it.type) {
                ST.Identifier -> HIR.IdentPattern(it.ref, it.content)
                ST.Wildcard -> HIR.WildcardPattern(it.ref)
                else -> error("Unsupported pattern type: ${it.type}")
            }
        } ?: HIRError.Propagation.at(node.ref)

    private fun BC.bindParamList(node: RedNode): List<HIR.Param> = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it)
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.ref, name, type)
        }
        ?.toList()
        ?: emptyList()

    private fun BC.bindFunction(node: RedNode): HIR.Function {
        val params = bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val context = FunctionBindingContext(
            this, params.asSequence().mapNotNull { it.pattern as? HIR.IdentPattern }
                .associateBy({ it.name }, { LocalRef((it)) })
        )
        val body = node.lastChild(SyntaxType.ScopeExpr)?.let { context.bindScope(it) }

        return HIR.Function(node.ref, params, returnType, body)
    }

    private fun BC.bindGetter(node: RedNode): HIR.Getter {
        val params = bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val context = FunctionBindingContext(
            this, params.asSequence().mapNotNull { it.pattern as? HIR.IdentPattern }
                .associateBy({ it.name }, { LocalRef((it)) })
        )
        val body = node.lastChild(SyntaxType.ScopeExpr)?.let { context.bindScope(it) }

        return HIR.Getter(node.ref, params, returnType, body)
    }

    private fun LC.bindCall(node: RedNode): HIR.CallExpr {
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

    private fun BC.bindIdentifier(node: RedNode) = refFor(node.content)
        ?.let { HIR.IdentExpr(node.ref, it) }
        ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

    private fun LC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)
        ST.Identifier -> bindIdentifier(node)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLet(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun LC.bindParenthesizedExpr(node: RedNode): HIR.Expr =
        node.firstChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

    private fun LC.bindLet(node: RedNode): HIR.LetExpr {
        val pattern = findAndBindPattern(node)
        val type = findAndBindTypeAnnotation(node)
        val expr = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

        if (pattern is HIR.IdentPattern)
            addLocal(pattern.name, pattern)

        return HIR.LetExpr(node.ref, pattern, type, expr)
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