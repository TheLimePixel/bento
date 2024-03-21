package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.ast.ASTRef
import io.github.thelimepixel.bento.binding.*

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
        return if (expr.type == type) expr else THIRError.InvalidType.at(hir.ref, listOf(expr), type)
    }

    private fun TC.typeIdentExpr(hir: HIR.Path): THIR {
        if (hir.binding.type != AccessorType.Get)
            return THIRError.InvalidIdentifierUse.at(hir.ref)

        return when (val binding = hir.binding.of) {
            is ItemRef -> when (binding.type) {
                ItemType.Getter ->
                    THIR.GetComputedExpr(hir.ref, typeOf(binding).accessType, binding)

                ItemType.StoredProperty ->
                    THIR.GetStoredExpr(hir.ref, typeOf(binding).accessType, binding)

                ItemType.SingletonType ->
                    THIR.SingletonAccessExpr(hir.ref, PathType(binding))

                ItemType.RecordType, ItemType.Function, ItemType.Field ->
                    THIRError.InvalidIdentifierUse.at(hir.ref)
            }

            is LocalRef -> THIR.LocalAccessExpr(hir.ref, typeOf(binding), binding)
        }
    }

    private fun FC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.AssignmentExpr -> typeAssignment(hir)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.ref)
        is HIR.Path -> typeIdentExpr(hir)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
        is HIR.LetExpr -> typeLetExpr(hir)
        is HIR.MemberAccessExpr -> typeAccessExpr(hir)
    }

    private fun FC.typeAccessExpr(hir: HIR.MemberAccessExpr): THIR {
        val on = typeExpr(hir.on, false)
        val member = memberOf(on.type.accessType.ref, hir.field) ?: return THIRError.UnknownMember.at(hir.ref)
        return THIR.GetFieldExpr(hir.ref, typeOf(member.of).accessType, member.of as ItemRef, on)
    }

    private fun FC.typeAssignment(hir: HIR.AssignmentExpr): THIR {
        val newValue = typeExpr(hir.right, false)
        return when (val left = hir.left) {
            is HIR.Path -> typeDirectAssignment(hir.ref, left, newValue)
            is HIR.MemberAccessExpr -> typeFieldAssignment(hir.ref, left, newValue)
            else -> THIRError.InvalidSetter.at(hir.ref, listOf(typeExpr(left, false), newValue))
        }
    }

    private fun FC.typeFieldAssignment(astRef: ASTRef, left: HIR.MemberAccessExpr, right: THIR): THIR {
        val on = typeExpr(left.on, false)
        val member = memberOf(on.type.accessType.ref, left.field + "_=") ?: return THIRError.UnknownMember.at(astRef)
        return THIR.SetFieldExpr(astRef, member.of as ItemRef, on, right)
    }

    private fun FC.typeDirectAssignment(astRef: ASTRef, left: HIR.Path, right: THIR): THIR {
        val type = typeOf(left.binding)
        if (type !is FunctionType)
            return THIRError.CallOnNonFunction.at(astRef, listOf(right))
        if (type.paramTypes.size != 1)
            return THIRError.InvalidSetter.at(astRef, listOf(right))
        val value =
            if (type.paramTypes[0] == right.type) right
            else THIRError.InvalidType.at(astRef, listOf(right))

        return when (val ref = left.binding.of) {
            is ItemRef -> when (ref.type) {
                ItemType.Function -> THIR.CallExpr(astRef, BuiltinTypes.unit, ref, listOf(value))
                ItemType.StoredProperty -> THIR.SetStoredExpr(astRef, ref, value)
                else -> THIRError.InvalidSetter.at(astRef)
            }

            is LocalRef -> THIR.LocalAssignmentExpr(astRef, ref, value)
        }
    }

    private fun FC.typeLetExpr(hir: HIR.LetExpr): THIR {
        val expr = hir.type.toPathType()?.let { expectExpr(hir.expr, it) } ?: typeExpr(hir.expr, false)

        setLetPattern(hir.pattern, expr.type)
        return THIR.LetExpr(hir.ref, hir.pattern?.local, expr)
    }

    private fun FC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, false) }
        val type = if (unit || statements.isEmpty()) BuiltinTypes.unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun FC.typeCall(hir: HIR.CallExpr): THIR {
        val binding = (hir.on as? HIR.Path)?.binding?.of
            ?: return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        if (binding is ItemRef) when (binding.type) {
            ItemType.Function -> return typeFunctionCall(binding, hir)
            ItemType.RecordType -> return typeConstructorCall(binding, hir)
            ItemType.SingletonType, ItemType.Getter, ItemType.StoredProperty, ItemType.Field -> Unit
        }

        return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })
    }

    private fun FC.typeArgs(args: List<HIR.Expr>, paramTypes: List<Type>): List<THIR> =
        if (args.size == paramTypes.size) {
            args.zip(paramTypes) { expr, type -> expectExpr(expr, type) }
        } else args.mapIndexed { index, expr ->
            if (index < paramTypes.size) expectExpr(expr, paramTypes[index])
            else THIRError.UnexpectedArgument.at(expr.ref, listOf(typeExpr(expr, false)))
        }

    private fun FC.typeConstructorCall(ref: ItemRef, hir: HIR.CallExpr): THIR {
        val typeHIR = hirOf(ref) as HIR.RecordType
        val paramTypes = typeHIR.constructor.fields.map { typeOf(it) }
        val args = typeArgs(hir.args, paramTypes)

        return THIR.ConstructorCallExpr(hir.ref, PathType(ref), args)
    }

    private fun FC.typeFunctionCall(ref: ItemRef, hir: HIR.CallExpr): THIR {
        val signature = typeOf(ref) as FunctionType
        val params = signature.paramTypes
        val args = typeArgs(hir.args, params)

        return THIR.CallExpr(hir.ref, signature.returnType, ref, args)
    }
}