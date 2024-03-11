package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.errors.ErrorType
import io.github.thelimepixel.bento.ast.ASTRef

enum class HIRError : ErrorType {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    UnboundIdentifier,
    UninitializedConstant,
    ;

    fun at(ref: ASTRef) = HIR.ErrorExpr(ref, this)
}