package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface HIR : CodeTree<HIR,  HIRError>, Spanned {
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
        val binding: FunctionRef,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: HIRError,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class FunctionDef(
        override val ref: ASTRef,
        val scope: ScopeExpr,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = sequenceOf(scope)
    }
}