package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
private typealias ST = SyntaxType
private typealias LC = LocalBindingContext

class BentoBinding {
    fun bind(items: List<ItemRef>, nodes: ItemMap, parentContext: BindingContext): Map<ItemRef, HIR.FunctionLike> {
        val context = FileBindingContext(
            parentContext,
            items.asSequence().filter { it.type.immutable }.associateBy { it.name },
            items.asSequence().filter { it.type.mutable }.associateBy { it.name },
        )
        return items.associateWith {
            context.bindDefinition(nodes[it.name]!![it.index].node.toRedRoot())
        }
    }

    private fun BC.bindDefinition(node: RedNode): HIR.FunctionLike = when (node.type) {
        ST.FunDef -> bindFunctionLike(node, HIR::Function)
        ST.GetDef -> bindFunctionLike(node, HIR::Getter)
        ST.SetDef -> bindFunctionLike(node, HIR::Setter)
        else -> error("Unsupported definition type")
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(SyntaxType.Identifier)
        ?.let {
            val itemRef = refForImmutable(it.content)
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

    private inline fun BC.bindFunctionLike(
        node: RedNode, ctor: (
            ref: ASTRef, params: List<HIR.Param>, returnType: HIR.TypeRef?, body: HIR.ScopeExpr?
        ) -> HIR.FunctionLike
    ): HIR.FunctionLike {
        val params = bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val context = FunctionBindingContext(
            this, params.asSequence().mapNotNull { it.pattern as? HIR.IdentPattern }
                .associateBy({ it.name }, { LocalRef((it)) })
        )
        val body = node.lastChild(SyntaxType.ScopeExpr)?.let { context.bindScope(it) }

        return ctor(node.ref, params, returnType, body)
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

    private fun BC.bindIdentifier(node: RedNode) = refForImmutable(node.content)
        ?.let { HIR.IdentExpr(node.ref, it) }
        ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

    private fun LC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)
        ST.Identifier -> bindIdentifier(node)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLet(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = node.firstChild(BaseSets.expressions)?.let { expr ->
            if (expr.type == ST.Identifier) refForMutable(expr.content)
            else null
        }
        val right = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.ref)
        return HIR.AssignmentExpr(node.ref, leftRef, right)
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