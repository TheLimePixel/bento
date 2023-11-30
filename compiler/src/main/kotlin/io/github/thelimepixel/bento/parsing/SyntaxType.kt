package io.github.thelimepixel.bento.parsing

enum class SyntaxType(
    val dynamic: Boolean = false,
    val ignore: Boolean = false,
    val error: ParseError? = null,
) {
    EOF,

    Unknown(dynamic = true, error = ParseError.UnknownSymbol),
    UnclosedComment(dynamic = true, error = ParseError.UnclosedComment, ignore = true),
    UnclosedString(dynamic = true, error = ParseError.UnclosedString),

    Whitespace(dynamic = true, ignore = true),
    Newline(ignore = true),
    LineComment(dynamic = true, ignore = true),
    MultiLineComment(dynamic = true, ignore = true),

    LParen,
    RParen,
    LBrace,
    RBrace,
    Comma,
    Colon,
    Equals,

    Identifier(dynamic = true),

    StringLiteral(dynamic = true),

    FunKeyword,
    LetKeyword,

    Param,
    ParamList,

    ArgList,

    IdentPattern,

    LetExpr,
    ScopeExpr,
    CallExpr,

    TypeAnnotation,

    FunDef,

    File,

    Error,
}

fun SyntaxType.edge(string: String) =
    GreenEdge(this, string)

fun SyntaxType.edge(code: String, start: Int, exclusiveEnd: Int) =
    GreenEdge(this, code.substring(start, exclusiveEnd))

object BaseEdges {
    val eof = SyntaxType.EOF.edge("")
    val nl = SyntaxType.Newline.edge( "\n")
    val lParen = SyntaxType.LParen.edge( "(")
    val rParen = SyntaxType.RParen.edge( ")")
    val lBrace = SyntaxType.LBrace.edge( "{")
    val rBrace = SyntaxType.RBrace.edge( "}")
    val comma = SyntaxType.Comma.edge( ",")
    val colon = SyntaxType.Colon.edge(":")
    val eq = SyntaxType.Equals.edge("=")
    val funKeyword = SyntaxType.FunKeyword.edge( "fun")
    val letKeyword = SyntaxType.LetKeyword.edge( "let")
    val loneSlash = SyntaxType.Unknown.edge( "/")
}