package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.utils.span

class ParseErrorCollector {
    private val errors = mutableListOf<ParseError>()

    fun push(kind: ParseErrorKind, start: Int, exclusiveEnd: Int) =
        errors.add(ParseError(span(start, exclusiveEnd), kind))

    fun errors(): List<ParseError> = errors
}