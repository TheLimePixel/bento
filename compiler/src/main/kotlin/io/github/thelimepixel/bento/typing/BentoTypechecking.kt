package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.HIR

class BentoTypechecking {
    fun type(hir: HIR.ScopeExpr, content: TypingContext): THIR.ScopeExpr =
        THIR.ScopeExpr(hir.ref, BentoType.Unit, hir.statements.map { typeExpr(it, content) })

    private fun typeExpr(hir: HIR, context: TypingContext): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir, context)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.ref)
        is HIR.IdentExpr -> THIRError.InvalidIdentifierUse.at(hir.ref)
        is HIR.ScopeExpr -> typeScope(hir, context)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
    }

    private fun typeScope(hir: HIR.ScopeExpr, content: TypingContext): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, content) }
        val type = if (statements.isEmpty()) BentoType.Unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun typeCall(hir: HIR.CallExpr, content: TypingContext): THIR {
        val on = hir.on as? HIR.IdentExpr ?: return THIRError.CallOnNonFunction.at(hir.ref)
        val signature = content.signatureOf(on.binding)
        val args = hir.args.map { typeExpr(it, content) }

        if (signature.paramTypes.size != args.size)
            return THIRError.InvalidArgumentCount.at(hir.ref)

        if (args.zip(signature.paramTypes).any { (arg, param) -> arg.type != param && arg.type != BentoType.Never })
            return THIRError.InvalidArgumentTypes.at(hir.ref)

        return THIR.CallExpr(hir.ref, signature.returnType, on.binding, args)
    }
}