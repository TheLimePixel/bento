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
    ExpectedClosedBrace,
    ExpectedScope,
    ExpectedIdentifier,
    ExpectedParameterList,
    ExpectedTypeAnnotation,
    ExpectedPattern,
    ;
}