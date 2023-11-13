package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.HIR

class BentoTypechecking {
    fun type(hir: HIR.ScopeExpr, content: TypingContext): THIR.ScopeExpr =
        THIR.ScopeExpr(hir.ref, BentoType.Unit, hir.statements.map { typeExpr(it, content) })

    private fun typeExpr(hir: HIR.Expr, context: TypingContext): THIR.Expr = when (hir) {
        is HIR.CallExpr -> typeCall(hir, context)
        is HIR.ErrorExpr -> when (hir.type) {
            HIR.ErrorExpr.Type.Unknown -> THIR.ErrorType.Unknown
            HIR.ErrorExpr.Type.InvalidIdentifier -> THIR.ErrorType.InvalidIdentifierUse
        }.at(hir.ref)

        is HIR.IdentExpr -> THIR.ErrorType.InvalidIdentifierUse.at(hir.ref)
        is HIR.ScopeExpr -> type(hir, context)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
    }

    fun typeScope(hir: HIR.ScopeExpr, content: TypingContext): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, content) }
        val type = if (statements.isEmpty()) BentoType.Unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun typeCall(hir: HIR.CallExpr, content: TypingContext): THIR.Expr {
        val on = hir.on as? HIR.IdentExpr ?: return THIR.ErrorType.CallOnNonFunction.at(hir.ref)
        val signature = content.signatureOf(on.binding)
        val args = hir.args.map { typeExpr(it, content) }

        if (signature.paramTypes.size != args.size)
            return THIR.ErrorType.InvalidArgumentCount.at(hir.ref)

        if (args.map { it.type } != signature.paramTypes)
            return THIR.ErrorType.InvalidArgumentTypes.at(hir.ref)

        return THIR.CallExpr(hir.ref, signature.returnType, on.binding, args)
    }
}