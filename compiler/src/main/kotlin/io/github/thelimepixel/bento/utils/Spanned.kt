package io.github.thelimepixel.bento.utils

interface Spanned {
    val span: Span
}

@JvmInline
value class Span(private val range: IntRange) {
    override fun toString(): String = range.toString()
}