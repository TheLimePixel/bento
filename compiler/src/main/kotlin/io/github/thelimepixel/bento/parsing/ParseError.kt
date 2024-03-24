package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.errors.ErrorKind
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.span

data class ParseError(val span: Span, val kind: ErrorKind)

enum class ParseErrorKind : ErrorKind {
    UnknownSymbol,
    UnclosedComment,
    UnclosedString,
    ExpectedDeclaration,
    ExpectedExpression,
    ExpectedEquals,
    ExpectedCommaOrClosedParen,
    ExpectedCommaOrClosedBrace,
    ExpectedClosedParen,
    ExpectedImportBlock,
    ExpectedIdentifier,
    ExpectedTypeAnnotation,
    ExpectedPattern,
    UnclosedRawIdentifier,
    ;
}