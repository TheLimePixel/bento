package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.ItemType
import io.github.thelimepixel.bento.binding.LocalRef

typealias TC = TypingContext
typealias FC = FunctionTypingContext

class BentoTypechecking {
    fun type(hir: HIR.Def, context: TC): THIR? = when (hir) {
        is HIR.FunctionLikeDef -> context.typeFunctionLikeDef(hir)
        is HIR.ConstantDef -> context.typeConstantDef(hir)
        is HIR.TypeDef -> null
    }

    private fun TC.typeFunctionLikeDef(hir: HIR.FunctionLikeDef): THIR? {
        val node = hir.body ?: return null
        val expect = hir.returnType.toType() ?: BuiltinTypes.unit
        val childContext = FunctionTypingContext(
            this,
            hir.params.mapNotNull {
                val pat = it.pattern as? HIR.IdentPattern ?: return@mapNotNull null
                LocalRef(pat) to (it.type.toType() ?: BuiltinTypes.nothing)
            }.toMap()
        )

        return childContext.expectExpr(node, expect)
    }

    private fun TC.typeConstantDef(hir: HIR.ConstantDef): THIR {
        val context = FunctionTypingContext(this, emptyMap())
        return context.expectExpr(hir.expr, hir.type.toType() ?: BuiltinTypes.unit)
    }

    private fun FC.expectExpr(hir: HIR.Expr, type: Type): THIR {
        val expr = typeExpr(hir, type == BuiltinTypes.unit)
        return if (expr.type == type) expr else THIRError.InvalidType.at(hir.ref, listOf(expr), type)
    }

    private fun TC.typeIdentExpr(hir: HIR.PathExpr): THIR = when (val binding = hir.binding) {
        is ItemRef -> when (binding.type) {
            ItemType.Getter ->
                THIR.CallExpr(hir.ref, typeOf(binding).accessType, binding, emptyList())

            ItemType.Constant ->
                THIR.CallExpr(hir.ref, typeOf(binding).accessType, binding, emptyList())

            ItemType.SingletonType ->
                THIR.SingletonAccessExpr(hir.ref, PathType(binding))

            ItemType.RecordType, ItemType.Setter, ItemType.Function ->
                THIRError.InvalidIdentifierUse.at(hir.ref)
        }

        is LocalRef -> THIR.AccessExpr(hir.ref, typeOf(binding).accessType, binding)
    }

    private fun FC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.AssignmentExpr -> typeAssignment(hir)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.ref)
        is HIR.PathExpr -> typeIdentExpr(hir)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
        is HIR.LetExpr -> typeLetExpr(hir)
    }

    private fun FC.typeAssignment(hir: HIR.AssignmentExpr): THIR = hir.left?.let { left ->
        THIR.CallExpr(hir.ref, typeOf(left).accessType, left as ItemRef, listOf(typeExpr(hir.right, false)))
    } ?: THIRError.Propagation.at(hir.ref)

    private fun FC.typeLetExpr(hir: HIR.LetExpr): THIR {
        val expr = hir.type.toType()?.let { expectExpr(hir.expr, it) } ?: typeExpr(hir.expr, false)
        val pattern = hir.pattern

        return if (pattern is HIR.IdentPattern) {
            val ref = LocalRef(pattern)
            set(ref, expr.type)
            THIR.LetExpr(hir.ref, ref, expr)
        } else expr
    }

    private fun FC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, false) }
        val type = if (unit || statements.isEmpty()) BuiltinTypes.unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun FC.typeCall(hir: HIR.CallExpr): THIR {
        val on = hir.on as? HIR.PathExpr
            ?: return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        val binding = on.binding

        if (binding !is ItemRef || binding.type != ItemType.Function)
            return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        val signature = typeOf(on.binding) as FunctionType
        val params = signature.paramTypes
        val args = if (hir.args.size == params.size) {
            hir.args.zip(params) { expr, type -> expectExpr(expr, type) }
        } else hir.args.mapIndexed { index, expr ->
            if (index < params.size) expectExpr(expr, params[index])
            else THIRError.UnexpectedArgument.at(expr.ref, listOf(typeExpr(expr, false)))
        }

        return THIR.CallExpr(hir.ref, signature.returnType, binding, args)
    }
}