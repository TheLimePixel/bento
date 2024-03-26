package io.github.thelimepixel.bento.utils

interface CodeTree<ChildType> {
    fun childSequence(): Sequence<ChildType>
}