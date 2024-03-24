package io.github.thelimepixel.bento.errors

import io.github.thelimepixel.bento.utils.Span

data class CodeError<E: ErrorKind>(val type: E, val span: Span)