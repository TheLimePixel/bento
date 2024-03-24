package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.errors.ErrorKind
import io.github.thelimepixel.bento.utils.Span

enum class HIRError : ErrorKind {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    UnboundIdentifier,
    ;

    fun at(span: Span) = HIR.ErrorExpr(span, this)
}