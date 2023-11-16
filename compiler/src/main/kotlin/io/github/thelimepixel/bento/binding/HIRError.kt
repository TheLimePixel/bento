package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.errors.ErrorType

enum class HIRError : ErrorType {
    Propagation {
        override val ignore: Boolean
            get() = true
    },
    UnboundIdentifier
}