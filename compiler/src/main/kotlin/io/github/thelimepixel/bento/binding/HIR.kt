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

    data class ScopeExpr(
        override val ref: ASTRef,
        val statements: List<HIR>,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        val on: HIR,
        val args: List<HIR>,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(on)
            yieldAll(args)
        }
    }

    data class IdentExpr(
        override val ref: ASTRef,
        val binding: ItemRef,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: HIRError,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }
}