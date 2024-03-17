package io.github.thelimepixel.bento.ast

import io.github.thelimepixel.bento.parsing.ParseError

enum class SyntaxType(
    val dynamic: Boolean = false,
    val ignore: Boolean = false,
    val error: ParseError? = null,
) {
    EOF,

    Unknown(dynamic = true, error = ParseError.UnknownSymbol),
    UnclosedComment(dynamic = true, error = ParseError.UnclosedComment, ignore = true),
    UnclosedString(dynamic = true, error = ParseError.UnclosedString),
    UnclosedRawIdentifier(dynamic = true, error = ParseError.UnclosedRawIdentifier),

    Whitespace(dynamic = true, ignore = true),
    Newline(ignore = true),
    LineComment(dynamic = true, ignore = true),
    MultiLineComment(dynamic = true, ignore = true),

    LParen,
    RParen,
    LBrace,
    RBrace,
    Comma,
    Dot,
    Colon,
    Equals,
    ColonColon,

    StandardIdentifier(dynamic = true),
    BacktickedIdentifier(dynamic = true),
    StringLiteral(dynamic = true),

    DefKeyword,
    LetKeyword,
    ImportKeyword,
    DataKeyword,

    Wildcard,

    Param,
    ParamList,

    ArgList,

    Identifier,

    LetExpr,
    ScopeExpr,
    CallExpr,
    ParenthesizedExpr,
    AssignmentExpr,
    Path,
    AccessExpr,

    TypeAnnotation,

    FunDef,
    LetDef,
    TypeDef,

    Constructor,
    Field,

    IdentPattern,
    WildcardPattern,

    ImportBlock,
    ImportPath,
    ImportStatement,

    File,

    Error,
}

fun SyntaxType.edge(string: String) =
    GreenEdge(this, string)

fun SyntaxType.edge(code: String, start: Int, exclusiveEnd: Int) =
    GreenEdge(this, code.substring(start, exclusiveEnd))

object BaseEdges {
    val eof = SyntaxType.EOF.edge("")
    val nlN = SyntaxType.Newline.edge( "\n")
    val nlR = SyntaxType.Newline.edge( "\r")
    val nlRN = SyntaxType.Newline.edge( "\r\n")
    val lParen = SyntaxType.LParen.edge( "(")
    val rParen = SyntaxType.RParen.edge( ")")
    val lBrace = SyntaxType.LBrace.edge( "{")
    val rBrace = SyntaxType.RBrace.edge( "}")
    val comma = SyntaxType.Comma.edge( ",")
    val dot = SyntaxType.Dot.edge( ".")
    val colon = SyntaxType.Colon.edge(":")
    val eq = SyntaxType.Equals.edge("=")
    val colonColon = SyntaxType.ColonColon.edge("::")
    val defKeyword = SyntaxType.DefKeyword.edge( "def")
    val letKeyword = SyntaxType.LetKeyword.edge( "let")
    val importKeyword = SyntaxType.ImportKeyword.edge( "import")
    val dataKeyword = SyntaxType.DataKeyword.edge( "data")
    val loneSlash = SyntaxType.Unknown.edge( "/")
}