package io.github.thelimepixel.bento.utils

interface Spanned {
    val span: Span
}

@JvmInline
value class Span internal constructor(private val range: IntRange) {
    override fun toString(): String = range.toString()
}

fun span(start: Int, exclusiveEnd: Int) = Span(start..<exclusiveEnd)