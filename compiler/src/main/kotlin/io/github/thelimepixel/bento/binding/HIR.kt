package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface HIR : CodeTree<HIR, HIRError>, Spanned {
    val ref: ASTRef
    override val span: IntRange
        get() = ref.span
    override val error: HIRError?
        get() = null

    sealed interface Expr : HIR

    data class ScopeExpr(
        override val ref: ASTRef,
        val statements: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        val on: Expr,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(on)
            yieldAll(args)
        }
    }

    data class IdentExpr(
        override val ref: ASTRef,
        val binding: Ref,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    sealed interface Pattern : HIR

    data class IdentPattern(override val ref: ASTRef, val name: String) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class WildcardPattern(override val ref: ASTRef) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class LetExpr(
        override val ref: ASTRef,
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
        override val ref: ASTRef,
        override val error: HIRError,
    ) : Expr, Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class TypeRef(override val ref: ASTRef, val type: ItemPath?) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class Param(override val ref: ASTRef, val pattern: Pattern, val type: TypeRef?) : HIR {
        override fun childSequence(): Sequence<HIR> = type?.let { sequenceOf(it) } ?: EmptySequence
    }

    data class Function(
        override val ref: ASTRef,
        val params: List<Param>,
        val returnType: TypeRef?,
        val body: ScopeExpr?
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }
}