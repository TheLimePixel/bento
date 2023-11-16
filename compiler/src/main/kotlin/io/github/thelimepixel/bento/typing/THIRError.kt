package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.errors.ErrorType
import io.github.thelimepixel.bento.parsing.ASTRef

enum class THIRError : ErrorType {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    InvalidIdentifierUse,
    CallOnNonFunction,
    InvalidArgumentCount,
    InvalidArgumentTypes,
    ;

    fun at(ref: ASTRef) = THIR.ErrorExpr(ref, this)
}