package io.github.thelimepixel.bento.utils

import io.github.thelimepixel.bento.errors.ErrorKind

interface CodeTree<ChildType, Err: ErrorKind> {
    val error: Err?
    fun childSequence(): Sequence<ChildType>
}