package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.FunctionRef
import io.github.thelimepixel.bento.parsing.ASTRef

sealed interface THIR {
    val ref: ASTRef
    val type: BentoType

    sealed interface Expr : THIR
    data class ScopeExpr(
        override val ref: ASTRef,
        override val type: BentoType,
        val statements: List<Expr>,
    ) : Expr

    data class CallExpr(
        override val ref: ASTRef,
        override val type: BentoType,
        val fn: FunctionRef,
        val args: List<Expr>,
    ) : Expr

    data class ErrorExpr(
        override val ref: ASTRef,
        val errType: ErrorType,
    ) : Expr {
        override val type: BentoType
            get() = BentoType.Never
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr {
        override val type: BentoType
            get() = BentoType.String

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)
    }

    enum class ErrorType {
        Unknown,
        InvalidIdentifierUse,
        CallOnNonFunction,
        InvalidArgumentCount,
        InvalidArgumentTypes,
        ;

        fun at(ref: ASTRef) = ErrorExpr(ref, this)
    }
}