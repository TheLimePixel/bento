package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR> {
    val span: Span?

    val error: THIRError?
        get() = null

    sealed interface Def : THIR {
        val body: Expr?
    }

    data class FunctionDef(val params: List<Param>, override val body: ScopeExpr) : Def {
        override fun childSequence(): Sequence<THIR> = sequence {
            yieldAll(params)
            yield(body)
        }
        override val span: Span
            get() = body.span
    }

    data class Param(val ref: LocalRef) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
        override val span: Span
            get() = ref.span
    }

    data class StoredPropertyDef(override val span: Span, override val body: Expr?) : Def {
        override fun childSequence(): Sequence<THIR> = sequence {
            body?.let { yield(it) }
        }
    }

    data class GetterDef(override val span: Span, override val body: Expr?) : Def {
        override fun childSequence(): Sequence<THIR> = sequence {
            body?.let { yield(it) }
        }
    }

    data class SingletonDef(override val span: Span) : Def {
        override fun childSequence(): Sequence<THIR> = EmptySequence
        override val body: Expr?
            get() = null
    }

    data class ProductTypeDef(override val span: Span, val fields: List<FieldRef>) : Def {
        override fun childSequence(): Sequence<THIR> = EmptySequence
        override val body: Expr?
            get() = null
    }

    sealed interface Expr : THIR {
        val type: Type
    }

    data class ScopeExpr(
        override val span: Span,
        override val type: Type,
        val statements: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class CallExpr(
        override val span: Span,
        override val type: Type,
        val fn: FunctionRef,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class GetComputedExpr(
        override val span: Span,
        override val type: Type,
        val def: GetterRef,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class GetStoredExpr(
        override val span: Span,
        override val type: Type,
        val property: StoredPropertyRef,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class SetStoredExpr(
        override val span: Span,
        val property: StoredPropertyRef,
        val value: Expr,
    ) : Expr {
        override val type: Type
            get() = BuiltinTypes.unit

        override fun childSequence(): Sequence<THIR> = sequenceOf(value)
    }

    data class ConstructorCallExpr(
        override val span: Span,
        override val type: PathType,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val span: Span,
        override val error: THIRError,
        override val type: Type,
        val children: List<THIR>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = children.asSequence()
    }

    data class StringExpr(
        override val span: Span,
        val content: String,
    ) : Expr {
        override val type: Type
            get() = BuiltinTypes.string

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAccessExpr(
        override val span: Span?,
        override val type: Type,
        val binding: LocalRef,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAssignmentExpr(
        override val span: Span?,
        val binding: LocalRef,
        val value: Expr,
    ) : Expr {
        override val type: Type
            get() = BuiltinTypes.unit

        override fun childSequence(): Sequence<THIR> = sequenceOf(value)
    }

    data class GetFieldExpr(
        override val span: Span?,
        override val type: PathType,
        val field: FieldRef,
        val on: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = sequenceOf(on)
    }

    data class SetFieldExpr(
        override val span: Span,
        val field: FieldRef,
        val on: Expr,
        val value: Expr,
    ) : Expr {
        override val type: Type
            get() = BuiltinTypes.unit

        override fun childSequence(): Sequence<THIR> = sequenceOf(on, value)
    }

    data class SingletonAccessExpr(
        override val span: Span,
        override val type: PathType,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}