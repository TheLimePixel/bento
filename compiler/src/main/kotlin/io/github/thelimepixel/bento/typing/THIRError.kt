package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.errors.ErrorType
import io.github.thelimepixel.bento.utils.Span

enum class THIRError : ErrorType {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    InvalidIdentifierUse,
    CallOnNonFunction,
    UnexpectedArgument,
    InvalidSetter,
    InvalidType,
    UnknownMember,
    ;

    fun at(span: Span, children: List<THIR> = emptyList(), type: Type = BuiltinTypes.nothing) =
        THIR.ErrorExpr(span, this, type, children)
}