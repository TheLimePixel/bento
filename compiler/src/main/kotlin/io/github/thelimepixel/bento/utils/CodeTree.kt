package io.github.thelimepixel.bento.utils

import io.github.thelimepixel.bento.errors.ErrorType

interface CodeTree<ChildType, Err: ErrorType> {
    val error: Err?
    fun childSequence(): Sequence<ChildType>
}