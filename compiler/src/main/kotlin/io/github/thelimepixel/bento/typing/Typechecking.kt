package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.utils.Span

typealias TC = TypingContext
typealias FC = LocalTypingContext

interface Typechecking {
    fun type(hir: HIR.Def, context: TC): THIR?
}

class BentoTypechecking : Typechecking {
    override fun type(hir: HIR.Def, context: TC): THIR? = when (hir) {
        is HIR.FunctionDef -> context.typeFunctionDef(hir)
        is HIR.GetterDef -> context.typeGetterDef(hir)
        is HIR.LetDef -> context.typeConstantDef(hir)
        is HIR.TypeDef, is HIR.Field -> null
    }

    private fun TC.typeFunctionDef(hir: HIR.FunctionDef): THIR? {
        val node = hir.body ?: return null
        val expect = hir.returnType.toPathType() ?: BuiltinTypes.unit
        val childContext = LocalTypingContext(this)
        hir.params.forEach {
            childContext.setLetPattern(it.pattern, it.type.toPathType() ?: BuiltinTypes.nothing)
        }
        return childContext.expectExpr(node, expect)
    }

    private fun TC.typeGetterDef(hir: HIR.GetterDef): THIR? {
        val node = hir.body ?: return null
        val expect = hir.returnType.toPathType() ?: BuiltinTypes.unit
        return LocalTypingContext(this).expectExpr(node, expect)
    }

    private fun FC.setLetPattern(pattern: HIR.Pattern?, type: Type) {
        this[pattern?.local ?: return] = type
    }

    private fun TC.typeConstantDef(hir: HIR.LetDef): THIR {
        val context = LocalTypingContext(this)
        return context.expectExpr(hir.expr, hir.type.toPathType() ?: BuiltinTypes.unit)
    }

    private fun FC.expectExpr(hir: HIR.Expr, type: Type): THIR {
        val expr = typeExpr(hir, type == BuiltinTypes.unit)
        return if (expr.type == type) expr else THIRError.InvalidType.at(hir.span, listOf(expr), type)
    }

    private fun TC.typeIdentExpr(hir: HIR.Path): THIR {
        val accessor = hir.binding
        if (accessor?.type != AccessorType.Get)
            return THIRError.InvalidIdentifierUse.at(hir.span)

        return when (val ref = accessor.of) {
            is GetterRef -> THIR.GetComputedExpr(hir.span, typeOf(ref).accessType, ref)
            is StoredPropertyRef -> THIR.GetStoredExpr(hir.span, typeOf(ref).accessType, ref)
            is SingletonTypeRef -> THIR.SingletonAccessExpr(hir.span, PathType(ref))
            is ProductTypeRef, is FunctionRef, is FieldRef, is PackageRef -> THIRError.InvalidIdentifierUse.at(hir.span)
            is LocalRef -> THIR.LocalAccessExpr(hir.span, typeOf(ref), ref)
        }
    }

    private fun FC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.AssignmentExpr -> typeAssignment(hir)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.span)
        is HIR.Path -> typeIdentExpr(hir)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.span, hir.content)
        is HIR.LetExpr -> typeLetExpr(hir)
        is HIR.MemberAccessExpr -> typeAccessExpr(hir)
    }

    private fun FC.typeAccessExpr(hir: HIR.MemberAccessExpr): THIR {
        val on = typeExpr(hir.on, false)
        val member = memberOf(on.type.accessType.ref, hir.field) ?: return THIRError.UnknownMember.at(hir.span)
        return THIR.GetFieldExpr(hir.span, typeOf(member.of).accessType, member.of as FieldRef, on)
    }

    private fun FC.typeAssignment(hir: HIR.AssignmentExpr): THIR {
        val newValue = typeExpr(hir.right, false)
        return when (val left = hir.left) {
            is HIR.Path -> typeDirectAssignment(hir.span, left, newValue)
            is HIR.MemberAccessExpr -> typeFieldAssignment(hir.span, left, newValue)
            else -> THIRError.InvalidSetter.at(hir.span, listOf(typeExpr(left, false), newValue))
        }
    }

    private fun FC.typeFieldAssignment(span: Span, left: HIR.MemberAccessExpr, right: THIR): THIR {
        val on = typeExpr(left.on, false)
        val member = memberOf(on.type.accessType.ref, left.field + "_=") ?: return THIRError.UnknownMember.at(span)
        return THIR.SetFieldExpr(span, member.of as FieldRef, on, right)
    }

    private fun FC.typeDirectAssignment(span: Span, left: HIR.Path, right: THIR): THIR {
        val leftAccessor = left.binding ?: return THIRError.CallOnNonFunction.at(span, listOf(right))
        val type = typeOf(leftAccessor)
        if (type !is FunctionType)
            return THIRError.InvalidSetter.at(span, listOf(right))
        if (type.paramTypes.size != 1)
            return THIRError.InvalidSetter.at(span, listOf(right))
        val value =
            if (type.paramTypes[0] == right.type) right
            else THIRError.InvalidType.at(span, listOf(right))

        return when (val ref = leftAccessor.of) {
            is FunctionRef -> THIR.CallExpr(span, BuiltinTypes.unit, ref, listOf(value))
            is StoredPropertyRef -> THIR.SetStoredExpr(span, ref, value)
            is LocalRef -> THIR.LocalAssignmentExpr(span, ref, value)
            else -> THIRError.InvalidSetter.at(span)
        }
    }

    private fun FC.typeLetExpr(hir: HIR.LetExpr): THIR {
        val expr = hir.type.toPathType()?.let { expectExpr(hir.expr, it) } ?: typeExpr(hir.expr, false)

        setLetPattern(hir.pattern, expr.type)
        return THIR.LetExpr(hir.span, hir.pattern?.local, expr)
    }

    private fun FC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, false) }
        val type = if (unit || statements.isEmpty()) BuiltinTypes.unit else statements.last().type
        return THIR.ScopeExpr(hir.span, type, statements)
    }

    private fun FC.typeCall(hir: HIR.CallExpr): THIR {
        val binding = (hir.on as? HIR.Path)?.binding?.of
            ?: return THIRError.CallOnNonFunction.at(hir.span, hir.args.map { typeExpr(it, false) })

        return when (binding) {
            is FunctionRef -> typeFunctionCall(binding, hir)
            is ProductTypeRef -> typeConstructorCall(binding, hir)
            else -> THIRError.CallOnNonFunction.at(hir.span, hir.args.map { typeExpr(it, false) })
        }
    }

    private fun FC.typeArgs(args: List<HIR.Expr>, paramTypes: List<Type>): List<THIR> =
        if (args.size == paramTypes.size) {
            args.zip(paramTypes) { expr, type -> expectExpr(expr, type) }
        } else args.mapIndexed { index, expr ->
            if (index < paramTypes.size) expectExpr(expr, paramTypes[index])
            else THIRError.UnexpectedArgument.at(expr.span, listOf(typeExpr(expr, false)))
        }

    private fun FC.typeConstructorCall(ref: ProductTypeRef, hir: HIR.CallExpr): THIR {
        val typeHIR = hirOf(ref) as HIR.ProductType
        val paramTypes = typeHIR.fields.map { it.type.toPathType() ?: BuiltinTypes.nothing }
        val args = typeArgs(hir.args, paramTypes)

        return THIR.ConstructorCallExpr(hir.span, PathType(ref), args)
    }

    private fun FC.typeFunctionCall(ref: FunctionRef, hir: HIR.CallExpr): THIR {
        val signature = typeOf(ref) as FunctionType
        val params = signature.paramTypes
        val args = typeArgs(hir.args, params)

        return THIR.CallExpr(hir.span, signature.returnType, ref, args)
    }
}