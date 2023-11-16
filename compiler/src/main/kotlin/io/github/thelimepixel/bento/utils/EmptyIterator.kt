package io.github.thelimepixel.bento.utils

object EmptyIterator : Iterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun next(): Nothing = error("Called next on an empty iterator")
}

object EmptySequence : Sequence<Nothing> {
    override fun iterator(): Iterator<Nothing> = EmptyIterator
}