package io.github.thelimepixel.bento.errors

data class CodeError<E: ErrorType>(val type: E, val span: IntRange)