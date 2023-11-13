package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.ASTRef

sealed interface HIR {
    val ref: ASTRef

    sealed interface Expr : HIR
    data class ScopeExpr(
        override val ref: ASTRef,
        val statements: List<Expr>,
    ) : Expr

    data class CallExpr(
        override val ref: ASTRef,
        val on: Expr,
        val args: List<Expr>,
    ) : Expr

    data class IdentExpr(
        override val ref: ASTRef,
        val binding: FunctionRef,
    ) : Expr

    data class ErrorExpr(
        override val ref: ASTRef,
        val type: Type,
    ) : Expr {
        enum class Type {
            Unknown,
            InvalidIdentifier
        }
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr

    data class FunctionDef(
        override val ref: ASTRef,
        val scope: ScopeExpr,
    ) : HIR
}