package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.FunctionRef
import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR, THIRError>, Spanned {
    val ref: ASTRef
    val type: BentoType
    override val span: IntRange
        get() = ref.span

    override val error: THIRError?
        get() = null

    sealed interface Expr : THIR
    data class ScopeExpr(
        override val ref: ASTRef,
        override val type: BentoType,
        val statements: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        override val type: BentoType,
        val fn: FunctionRef,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: THIRError,
    ) : Expr {
        override val type: BentoType
            get() = BentoType.Never

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr {
        override val type: BentoType
            get() = BentoType.String

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}