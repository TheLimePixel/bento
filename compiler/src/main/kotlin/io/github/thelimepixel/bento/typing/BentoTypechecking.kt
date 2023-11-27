package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemType

typealias TC = TypingContext

class BentoTypechecking {
    fun type(hir: HIR.Expr, context: TypingContext, type: ItemPath): THIR =
        context.expectExpr(hir, type)

    private fun TC.expectExpr(hir: HIR.Expr, type: ItemPath): THIR {
        val expr = typeExpr(hir, type == BuiltinRefs.unit)
        return if (expr.type == type) expr else THIRError.InvalidType.at(hir.ref, listOf(expr), type)
    }

    private fun TC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR = when (hir) {
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
        val on = hir.on as? HIR.IdentExpr
            ?: return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        if (on.binding.type != ItemType.Function)
            return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        val signature = signatureOf(on.binding)
        val params = signature.paramTypes
        val args = if (hir.args.size == params.size) {
            hir.args.zip(params) { expr, type -> expectExpr(expr, type) }
        } else hir.args.mapIndexed { index, expr ->
            if (index < params.size) expectExpr(expr, params[index])
            else THIRError.UnexpectedArgument.at(expr.ref, listOf(typeExpr(expr, false)))
        }

        return THIR.CallExpr(hir.ref, signature.returnType, on.binding, args)
    }
}