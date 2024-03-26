package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.span

typealias LC = LocalTypingContext

interface Typechecking {
    fun type(hir: HIR.Def, context: TypingContext): TypingInfo
}

data class TypingInfo(val thir: THIR.Def?, val localTypes: Map<LocalRef, Type>)

typealias TypingMap = Map<ItemRef, TypingInfo>

val emptyTypingInfo = TypingInfo(null, emptyMap())

class BentoTypechecking : Typechecking {
    override fun type(hir: HIR.Def, context: TypingContext): TypingInfo {
        val locals = mutableMapOf<LocalRef, Type>()
        val localContext = LocalTypingContext(context, locals)
        val thir = when (hir) {
            is HIR.FunctionDef -> localContext.typeFunctionDef(hir)
            is HIR.GetterDef -> localContext.typeGetterDef(hir)
            is HIR.LetDef -> localContext.typeStoredPropertyDef(hir)
            is HIR.SingletonType -> THIR.SingletonDef(hir.span)
            is HIR.ProductType -> THIR.ProductTypeDef(hir.span, hir.fields.map { it.ref })
            is HIR.Field -> return emptyTypingInfo
        }
        return TypingInfo(thir, locals)
    }

    private fun LC.typeFunctionDef(hir: HIR.FunctionDef): THIR.Def {
        val statements = mutableListOf<THIR.Expr>()
        val params: List<THIR.Param> = hir.params.map {
            unwindPattern(it.pattern, it.type.toPathType() ?: BuiltinTypes.nothing, statements)
        }
        val type = hir.returnType.toPathType() ?: BuiltinTypes.unit
        hir.body?.let { node -> statements.add(expectExpr(node, type)) }
        return THIR.FunctionDef(params, THIR.ScopeExpr(hir.span, type, statements))
    }

    private fun LC.typeGetterDef(hir: HIR.GetterDef): THIR.Def {
        val node = hir.body ?: return THIR.GetterDef(hir.span, null)
        val expect = hir.returnType.toPathType() ?: BuiltinTypes.unit
        return THIR.GetterDef(hir.span, expectExpr(node, expect))
    }

    private fun LC.unwindPattern(hir: HIR.Pattern?, type: Type, body: MutableList<THIR.Expr>): THIR.Param = when (hir) {
        null, is HIR.Error -> THIR.Param(LocalRef(span(0, 0)))
        is HIR.DestructurePattern -> {
            // TODO: Check type equality
            val newLocal = LocalRef(hir.span)
            set(newLocal, type)
            unwindDestructureChildren(type, hir, newLocal, body)
            THIR.Param(newLocal)
        }

        is HIR.IdentPattern -> {
            set(hir.local, type)
            THIR.Param(hir.local)
        }

        is HIR.MutablePattern ->
            unwindPattern(hir.nested, type, body)

        is HIR.PathPattern -> {
            // TODO: Check type equality
            val newLocal = LocalRef(hir.span)
            set(newLocal, type)
            THIR.Param(newLocal)
        }

        is HIR.WildcardPattern -> {
            val newLocal = LocalRef(hir.span)
            set(newLocal, type)
            THIR.Param(newLocal)
        }
    }

    private fun LC.unwindDestructureChildren(
        type: Type,
        hir: HIR.DestructurePattern,
        local: LocalRef,
        body: MutableList<THIR.Expr>
    ) {
        val ctor = hirOf(type.accessType.ref) as? HIR.ProductType ?: return
        hir.fields.zip(ctor.fields) { pat, subField ->
            val subFieldRef = subField.ref
            handleDestructuring(pat, local, subFieldRef, typeOf(subFieldRef), body)
        }
    }

    private fun LC.handleDestructuring(
        hir: HIR.Pattern?,
        parent: LocalRef,
        field: FieldRef,
        type: Type,
        body: MutableList<THIR.Expr>
    ) {
        when (hir) {
            null, is HIR.Error, is HIR.WildcardPattern -> Unit
            is HIR.DestructurePattern -> {
                // TODO: Check equality
                val newLocal = LocalRef(hir.span)
                generateDestructureFieldAssignment(field, parent, newLocal, type, body)
                unwindDestructureChildren(type, hir, newLocal, body)
            }

            is HIR.IdentPattern ->
                generateDestructureFieldAssignment(field, parent, hir.local, type, body)


            is HIR.MutablePattern ->
                handleDestructuring(hir.nested, parent, field, type, body)

            is HIR.PathPattern -> {
                // TODO: Check equality
            }
        }
    }

    private fun LC.unwindLetPattern(
        hir: HIR.Pattern?,
        span: Span,
        expr: THIR.Expr,
        body: MutableList<THIR.Expr>
    ) {
        when (hir) {
            null, is HIR.Error, is HIR.WildcardPattern -> body.add(expr)
            is HIR.DestructurePattern -> {
                // TODO: Check equality
                val newLocal = LocalRef(hir.span)
                val type = expr.type
                set(newLocal, type)
                body.add(THIR.LocalAssignmentExpr(span, newLocal, expr))
                unwindDestructureChildren(type, hir, newLocal, body)
            }

            is HIR.IdentPattern -> {
                set(hir.local, expr.type)
                body.add(THIR.LocalAssignmentExpr(span, hir.local, expr))
            }


            is HIR.MutablePattern ->
                unwindLetPattern(hir.nested, span, expr, body)

            is HIR.PathPattern -> {
                // TODO: Check equality
                body.add(expr)
            }
        }
    }

    private fun LC.generateDestructureFieldAssignment(
        field: FieldRef,
        parent: LocalRef,
        newLocal: LocalRef,
        type: Type,
        body: MutableList<THIR.Expr>
    ) {
        val parentType = typeOf(parent)
        set(newLocal, type)
        val access = THIR.GetFieldExpr(null, type.accessType, field, THIR.LocalAccessExpr(null, parentType, parent))
        body.add(THIR.LocalAssignmentExpr(null, newLocal, access))
    }

    private fun LC.typeStoredPropertyDef(hir: HIR.LetDef): THIR.Def =
        THIR.StoredPropertyDef(hir.span, expectExpr(hir.expr, hir.type.toPathType() ?: BuiltinTypes.unit))

    private fun LC.expectExpr(hir: HIR.Expr, type: Type): THIR.Expr {
        val expr = typeExpr(hir, type == BuiltinTypes.unit)
        return if (expr.type == type) expr else THIRError.InvalidType.at(hir.span, listOf(expr), type)
    }

    private fun LC.typeIdentExpr(hir: HIR.Path): THIR.Expr {
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

    private fun LC.typeExpr(hir: HIR.Expr, unit: Boolean): THIR.Expr = when (hir) {
        is HIR.CallExpr -> typeCall(hir)
        is HIR.AssignmentExpr -> typeAssignment(hir)
        is HIR.Error -> THIRError.Propagation.at(hir.span)
        is HIR.Path -> typeIdentExpr(hir)
        is HIR.ScopeExpr -> typeScope(hir, unit)
        is HIR.StringExpr -> THIR.StringExpr(hir.span, hir.content)
        is HIR.MemberAccessExpr -> typeAccessExpr(hir)
    }

    private fun LC.addStatement(
        hir: HIR.Statement,
        unit: Boolean,
        body: MutableList<THIR.Expr>
    ) {
        when (hir) {
            is HIR.LetStatement -> typeLetExpr(hir, body)
            is HIR.Expr -> body.add(typeExpr(hir, unit))
        }
    }

    private fun LC.typeAccessExpr(hir: HIR.MemberAccessExpr): THIR.Expr {
        val on = typeExpr(hir.on, false)
        val member = memberOf(on.type.accessType.ref, hir.field) ?: return THIRError.UnknownMember.at(hir.span)
        return THIR.GetFieldExpr(hir.span, typeOf(member.of).accessType, member.of as FieldRef, on)
    }

    private fun LC.typeAssignment(hir: HIR.AssignmentExpr): THIR.Expr {
        val newValue = typeExpr(hir.right, false)
        return when (val left = hir.left) {
            is HIR.Path -> typeDirectAssignment(hir.span, left, newValue)
            is HIR.MemberAccessExpr -> typeFieldAssignment(hir.span, left, newValue)
            else -> THIRError.InvalidSetter.at(hir.span, listOf(typeExpr(left, false), newValue))
        }
    }

    private fun LC.typeFieldAssignment(span: Span, left: HIR.MemberAccessExpr, right: THIR.Expr): THIR.Expr {
        val on = typeExpr(left.on, false)
        val member = memberOf(on.type.accessType.ref, left.field + "_=") ?: return THIRError.UnknownMember.at(span)
        return THIR.SetFieldExpr(span, member.of as FieldRef, on, right)
    }

    private fun LC.typeDirectAssignment(span: Span, left: HIR.Path, right: THIR.Expr): THIR.Expr {
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

    private fun LC.typeLetExpr(hir: HIR.LetStatement, body: MutableList<THIR.Expr>) {
        val expr = hir.type.toPathType()?.let { expectExpr(hir.expr, it) } ?: typeExpr(hir.expr, false)
        unwindLetPattern(hir.pattern, hir.span, expr, body)
    }

    private fun LC.typeScope(hir: HIR.ScopeExpr, unit: Boolean): THIR.ScopeExpr {
        val statements = mutableListOf<THIR.Expr>()
        hir.statements.forEach { addStatement(it, false, statements) }
        val type = if (unit || statements.isEmpty()) BuiltinTypes.unit else statements.last().type
        return THIR.ScopeExpr(hir.span, type, statements)
    }

    private fun LC.typeCall(hir: HIR.CallExpr): THIR.Expr {
        val binding = (hir.on as? HIR.Path)?.binding?.of
            ?: return THIRError.CallOnNonFunction.at(hir.span, hir.args.map { typeExpr(it, false) })

        return when (binding) {
            is FunctionRef -> typeFunctionCall(binding, hir)
            is ProductTypeRef -> typeConstructorCall(binding, hir)
            else -> THIRError.CallOnNonFunction.at(hir.span, hir.args.map { typeExpr(it, false) })
        }
    }

    private fun LC.typeArgs(args: List<HIR.Expr>, paramTypes: List<Type>): List<THIR.Expr> =
        if (args.size == paramTypes.size) {
            args.zip(paramTypes) { expr, type -> expectExpr(expr, type) }
        } else args.mapIndexed { index, expr ->
            if (index < paramTypes.size) expectExpr(expr, paramTypes[index])
            else THIRError.UnexpectedArgument.at(expr.span, listOf(typeExpr(expr, false)))
        }

    private fun LC.typeConstructorCall(ref: ProductTypeRef, hir: HIR.CallExpr): THIR.Expr {
        val typeHIR = hirOf(ref) as HIR.ProductType
        val paramTypes = typeHIR.fields.map { it.type.toPathType() ?: BuiltinTypes.nothing }
        val args = typeArgs(hir.args, paramTypes)

        return THIR.ConstructorCallExpr(hir.span, PathType(ref), args)
    }

    private fun LC.typeFunctionCall(ref: FunctionRef, hir: HIR.CallExpr): THIR.Expr {
        val signature = typeOf(ref) as FunctionType
        val params = signature.paramTypes
        val args = typeArgs(hir.args, params)

        return THIR.CallExpr(hir.span, signature.returnType, ref, args)
    }
}