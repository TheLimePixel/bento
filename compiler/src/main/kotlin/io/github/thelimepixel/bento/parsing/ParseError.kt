package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.utils.Span

data class ParseError(val span: Span, val kind: ParseErrorKind)

enum class ParseErrorKind {
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