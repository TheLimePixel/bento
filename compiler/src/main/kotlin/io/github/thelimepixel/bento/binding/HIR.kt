package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.Spanned

sealed interface HIR : CodeTree<HIR, HIRError>, Spanned {
    override val span: Span
    override val error: HIRError?
        get() = null

    sealed interface Expr : HIR

    data class ScopeExpr(
        override val span: Span,
        val statements: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = statements.asSequence()
    }

    data class CallExpr(
        override val span: Span,
        val on: Expr,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(on)
            yieldAll(args)
        }
    }

    data class AssignmentExpr(
        override val span: Span,
        val left: Expr,
        val right: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(left)
            yield(right)
        }
    }

    data class Path(
        override val span: Span,
        val binding: Accessor,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    sealed interface Pattern : HIR {
        val local: LocalRef?
    }

    data class IdentPattern(override val span: Span, override val local: LocalRef) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class MutablePattern(override val span: Span, val nested: Pattern?) : Pattern {
        override fun childSequence(): Sequence<HIR> = sequence {
            nested?.let { yield(it) }
        }

        override val local: LocalRef?
            get() = nested?.local
    }

    data class WildcardPattern(override val span: Span) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
        override val local: LocalRef?
            get() = null
    }

    data class LetExpr(
        override val span: Span,
        val pattern: Pattern?,
        val type: TypeRef?,
        val expr: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            pattern?.let { yield(it) }
            type?.let { yield(it) }
            yield(expr)
        }
    }

    data class ErrorExpr(
        override val span: Span,
        override val error: HIRError,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val span: Span,
        val content: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class MemberAccessExpr(
        override val span: Span,
        val on: Expr,
        val field: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequenceOf(on)
    }

    data class TypeRef(override val span: Span, val type: Path?) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            type?.let { yield(it) }
        }
    }

    data class Param(override val span: Span, val pattern: Pattern?, val type: TypeRef?) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            pattern?.let { yield(it) }
            type?.let { yield(it) }
        }
    }

    sealed interface Def : HIR

    data class FunctionDef(
        override val span: Span,
        val params: List<Param>,
        val returnType: TypeRef?,
        val body: Expr?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            yieldAll(params)
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }

    data class GetterDef(
        override val span: Span,
        val returnType: TypeRef?,
        val body: Expr?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }

    data class LetDef(
        override val span: Span,
        val type: TypeRef?,
        val expr: Expr,
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            type?.let { yield(it) }
            yield(expr)
        }
    }

    sealed interface TypeDef : Def

    data class SingletonType(override val span: Span) : TypeDef {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class Field(
        override val span: Span,
        val ref: FieldRef,
        val type: TypeRef?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence { type?.let { yield(it) } }
    }

    data class ProductType(override val span: Span, val fields: List<Field>) : TypeDef {
        override fun childSequence(): Sequence<HIR> = fields.asSequence()
    }
}