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
    Colon,
    Equals,
    ColonColon,

    StandardIdentifier(dynamic = true),
    BacktickedIdentifier(dynamic = true),
    StringLiteral(dynamic = true),

    FunKeyword,
    LetKeyword,
    ImportKeyword,

    GetKeyword,
    SetKeyword,

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
    PathExpr,

    TypeAnnotation,

    FunDef,
    GetDef,
    SetDef,
    LetDef,

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
    val colon = SyntaxType.Colon.edge(":")
    val eq = SyntaxType.Equals.edge("=")
    val colonColon = SyntaxType.ColonColon.edge("::")
    val funKeyword = SyntaxType.FunKeyword.edge( "fun")
    val letKeyword = SyntaxType.LetKeyword.edge( "let")
    val getKeyword = SyntaxType.GetKeyword.edge( "get")
    val setKeyword = SyntaxType.SetKeyword.edge( "set")
    val importKeyword = SyntaxType.ImportKeyword.edge( "import")
    val loneSlash = SyntaxType.Unknown.edge( "/")
}