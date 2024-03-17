package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.errors.ErrorType

enum class ParseError : ErrorType {
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