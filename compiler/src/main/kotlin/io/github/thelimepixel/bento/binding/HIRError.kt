package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.errors.ErrorType
import io.github.thelimepixel.bento.utils.Span

enum class HIRError : ErrorType {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    UnboundIdentifier,
    ;

    fun at(span: Span) = HIR.ErrorExpr(span, this)
}