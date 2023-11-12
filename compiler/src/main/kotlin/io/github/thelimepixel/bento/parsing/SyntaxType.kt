package io.github.thelimepixel.bento.parsing

enum class SyntaxType(
    val dynamic: Boolean = false,
    val ignore: Boolean = false,
) {
    EOF,

    Unknown(dynamic = true),

    Whitespace(dynamic = true, ignore = true),
    Newline(ignore = true),

    LParen,
    RParen,
    LBrace,
    RBrace,
    Comma,

    Identifier(dynamic = true),

    StringLiteral(dynamic = true),

    FunKeyword,

    ParamList,
    ArgList,

    ScopeExpr,
    CallExpr,

    FunDef,

    File
}

object BaseEdges {
    val eof = GreenEdge(SyntaxType.EOF, "")
    val nl = GreenEdge(SyntaxType.Newline, "\n")
    val lParen = GreenEdge(SyntaxType.LParen, "(")
    val rParen = GreenEdge(SyntaxType.RParen, ")")
    val lBrace = GreenEdge(SyntaxType.LBrace, "{")
    val rBrace = GreenEdge(SyntaxType.RBrace, "}")
    val comma = GreenEdge(SyntaxType.Comma, ",")
    val funKeyword = GreenEdge(SyntaxType.FunKeyword, "fun")
}