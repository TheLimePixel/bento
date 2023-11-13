package io.github.thelimepixel.bento.parsing

class Lexer(private val code: String, private var pos: Int = 0) {
    var current: GreenEdge = getAt(pos)
        private set

    val currentSpan: IntRange
        get() = pos..(pos + current.length)

    private val Int.isIndex get() = this < code.length

    private fun getAt(index: Int): GreenEdge =
        if (index == code.length) BaseEdges.eof else when (val char = code[index]) {
            '(' -> BaseEdges.lParen
            ')' -> BaseEdges.rParen
            '{' -> BaseEdges.lBrace
            '}' -> BaseEdges.rBrace
            ',' -> BaseEdges.comma
            '\n' -> BaseEdges.nl
            ' ', '\t', '\r', '\u000C', '\u2B7F' -> getWhitespace(pos + 1)
            '_', in 'a'..'z', in 'A'..'Z' -> getIdentifier(pos + 1)
            '\"' -> getString(pos + 1)
            else -> when {
                char.isLetter() -> getIdentifier(pos + 1)
                char.isWhitespace() -> getWhitespace(pos + 1)
                else -> GreenEdge(SyntaxType.Unknown, char.toString())
            }
        }

    private fun finishStringLiteral(end: Int): GreenEdge =
        GreenEdge(SyntaxType.StringLiteral, code.substring(pos, end))

    private tailrec fun getString(curr: Int): GreenEdge =
        if (!curr.isIndex) finishStringLiteral(curr) else when (code[curr]) {
            '\"' -> finishStringLiteral(curr + 1)
            else -> getString(curr + 1)
        }

    private fun isWhitespace(c: Char) = when (c) {
        '\n' -> false
        ' ', '\t', '\r', '\u000C', '\u2B7F' -> true
        else -> c.isWhitespace()
    }

    private tailrec fun getWhitespace(curr: Int): GreenEdge =
        if (curr.isIndex && isWhitespace(code[curr])) getWhitespace(curr + 1)
        else GreenEdge(SyntaxType.Whitespace, code.substring(pos, curr))

    private fun isIdentBody(c: Char): Boolean = when (c) {
        '_', '\'', in 'a'..'z', in 'A'..'Z', in '0'..'9' -> true
        else -> c.isLetterOrDigit()
    }

    private tailrec fun getIdentifier(curr: Int): GreenEdge =
        if (curr.isIndex && isIdentBody(code[curr])) getIdentifier(curr + 1)
        else code.substring(pos, curr).matchIdentifier()

    private fun String.matchIdentifier() = when (this) {
        "fun" -> BaseEdges.funKeyword
        else -> GreenEdge(SyntaxType.Identifier, this)
    }

    fun move() {
        pos += current.length
        current = getAt(pos)
    }
}