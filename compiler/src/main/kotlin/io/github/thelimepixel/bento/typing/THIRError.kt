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
    NotEnoughArguments,
    UnexpectedArgument,
    InvalidType,
    ;

    fun at(ref: ASTRef, children: List<THIR> = emptyList(), type: Type = BuiltinTypes.nothing) =
        THIR.ErrorExpr(ref, this, type, children)
}