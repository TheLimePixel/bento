package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*

typealias TC = TypingContext
typealias FC = LocalTypingContext

interface Typechecking {
    fun type(hir: HIR.Def, context: TC): THIR?
}

class BentoTypechecking : Typechecking {
    override fun type(hir: HIR.Def, context: TC): THIR? = when (hir) {
        is HIR.FunctionLikeDef -> context.typeFunctionLikeDef(hir)
        is HIR.ConstantDef -> context.typeConstantDef(hir)
        is HIR.TypeDef, is HIR.Field -> null
    }

    private fun TC.typeFunctionLikeDef(hir: HIR.FunctionLikeDef): THIR? {
        val node = hir.body ?: return null
        val expect = hir.returnType.toType() ?: BuiltinTypes.unit
        val childContext = LocalTypingContext(this)
        hir.params?.forEach {
            childContext.setLetPattern(it.pattern, it.type.toType() ?: BuiltinTypes.nothing)
        }
        return childContext.expectExpr(node, expect)
    }

    private fun FC.setLetPattern(pattern: HIR.Pattern?, type: Type) {
        val (getter, setter) = pattern?.getRefs() ?: return
        if (getter == null) return
        this[getter] = type
        if (setter != null) this[setter] = FunctionType(listOf(type), BuiltinTypes.unit)
    }

    private fun TC.typeConstantDef(hir: HIR.ConstantDef): THIR {
        val context = LocalTypingContext(this)
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

            ItemType.RecordType, ItemType.Function, ItemType.Field ->
                THIRError.InvalidIdentifierUse.at(hir.ref)
        }

        is AccessorRef -> when (binding.type){
            AccessorType.Getter -> THIR.LocalAccessExpr(hir.ref, typeOf(binding).accessType, binding.of)
            AccessorType.Setter -> THIRError.InvalidIdentifierUse.at(hir.ref)
        }
    }

    private fun FC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.AssignmentExpr -> typeAssignment(hir)
        is HIR.ErrorExpr -> THIRError.Propagation.at(hir.ref)
        is HIR.PathExpr -> typeIdentExpr(hir)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.ref, hir.content)
        is HIR.LetExpr -> typeLetExpr(hir)
        is HIR.AccessExpr -> typeAccessExpr(hir)
    }

    private fun FC.typeAccessExpr(hir: HIR.AccessExpr): THIR {
        val on = typeExpr(hir.on, false)
        val member = memberOf(on.type.accessType.ref, hir.field) ?: return THIRError.UnknownMember.at(hir.ref)
        return THIR.FieldAccessExpr(hir.ref, typeOf(member).accessType, member, on)
    }

    private fun FC.typeAssignment(hir: HIR.AssignmentExpr): THIR = hir.left?.let { left ->
        val newValue = typeExpr(hir.right, false)
        val type = typeOf(left)
        if (type !is FunctionType) return@let THIRError.CallOnNonFunction.at(hir.ref, listOf(newValue))
        if (type.paramTypes.size != 1) return@let THIRError.InvalidSetter.at(hir.ref, listOf(newValue))
        val value =
            if (type.paramTypes[0] == newValue.type) newValue
            else THIRError.InvalidType.at(hir.ref, listOf(newValue))
        when (left) {
            is ItemRef -> THIR.CallExpr(hir.ref, BuiltinTypes.unit, left, listOf(value))
            is AccessorRef -> THIR.LocalAssignmentExpr(hir.ref, left.of, value)
        }
    } ?: THIRError.Propagation.at(hir.ref)

    private fun FC.typeLetExpr(hir: HIR.LetExpr): THIR {
        val expr = hir.type.toType()?.let { expectExpr(hir.expr, it) } ?: typeExpr(hir.expr, false)

        setLetPattern(hir.pattern, expr.type)
        return THIR.LetExpr(hir.ref, hir.pattern?.findId(), expr)
    }

    private fun FC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = hir.statements.map { typeExpr(it, false) }
        val type = if (unit || statements.isEmpty()) BuiltinTypes.unit else statements.last().type
        return THIR.ScopeExpr(hir.ref, type, statements)
    }

    private fun FC.typeCall(hir: HIR.CallExpr): THIR {
        val binding = (hir.on as? HIR.PathExpr)?.binding
            ?: return THIRError.CallOnNonFunction.at(hir.ref, hir.args.map { typeExpr(it, false) })

        if (binding is ItemRef) when (binding.type) {
            ItemType.Function -> return typeFunctionCall(binding, hir)
            ItemType.RecordType -> return typeConstructorCall(binding, hir)
            ItemType.SingletonType, ItemType.Getter, ItemType.Constant, ItemType.Field -> Unit
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