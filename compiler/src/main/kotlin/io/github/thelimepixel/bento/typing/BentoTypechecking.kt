package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR

typealias TC = TypingContext

class BentoTypechecking {
    fun type(hir: HIR, context: TypingContext): THIR =
        context.typeExpr(hir, true)

    private fun TC.typeExpr(hir: HIR, unit: Boolean): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.ref)
        is HIR.IdentExpr -> THIRError.InvalidIdentifierUse.at(hir.ref)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
    }

    private fun TC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, false) }
        val type = if (unit || statements.isEmpty()) BuiltinRefs.unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun TC.typeCall(hir: HIR.CallExpr): THIR {
        val on = hir.on as? HIR.IdentExpr ?: return THIRError.CallOnNonFunction.at(hir.ref)
        val signature = signatureOf(on.binding)
        val args = hir.args.map { typeExpr(it, false) }

        if (signature.paramTypes.size != args.size)
            return THIRError.InvalidArgumentCount.at(hir.ref)

        if (args.zip(signature.paramTypes).any { (arg, param) -> arg.type != param && arg.type != BuiltinRefs.nothing })
            return THIRError.InvalidArgumentTypes.at(hir.ref)

        return THIR.CallExpr(hir.ref, signature.returnType, on.binding, args)
    }
}