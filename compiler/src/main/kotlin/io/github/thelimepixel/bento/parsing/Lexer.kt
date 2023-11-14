package io.github.thelimepixel.bento.parsing

private const val eofChar = Char.MAX_VALUE

class Lexer(private val code: String, private var pos: Int = 0) {
    var current: GreenEdge = edgeAt(pos)
        private set

    val currentSpan: IntRange
        get() = pos..(pos + current.length)

    private fun at(index: Int): Char =
        if (index in code.indices) code[index] else eofChar

    private fun edgeAt(index: Int): GreenEdge = when (val char = at(index)) {
        eofChar -> BaseEdges.eof
        '(' -> BaseEdges.lParen
        ')' -> BaseEdges.rParen
        '{' -> BaseEdges.lBrace
        '}' -> BaseEdges.rBrace
        ',' -> BaseEdges.comma
        '\n' -> BaseEdges.nl
        '/' -> getSlash(index + 1)
        ' ', '\t', '\r', '\u000C', '\u2B7F' -> getWhitespace(index + 1)
        '_', in 'a'..'z', in 'A'..'Z' -> getIdentifier(index + 1)
        '\"' -> getString(index + 1)
        else -> when {
            char.isLetter() -> getIdentifier(index + 1)
            char.isWhitespace() -> getWhitespace(index + 1)
            else -> SyntaxType.Unknown.edge(char.toString())
        }
    }

    private fun getSlash(curr: Int): GreenEdge = when (at(curr)) {
        '/' -> getLineComment(curr + 1)

        '*' -> {
            val endPos = getMultilineComment(curr + 1)
            if (endPos > code.length)
                SyntaxType.UnclosedComment.edge(code, pos, code.length)
            else
                SyntaxType.MultiLineComment.edge(code, pos, endPos)
        }

        else -> BaseEdges.loneSlash
    }

    private tailrec fun getLineComment(curr: Int): GreenEdge = when (at(curr)) {
        eofChar, '\n' -> SyntaxType.LineComment.edge(code, pos, curr)
        else -> getLineComment(curr + 1)
    }

    @Suppress("NON_TAIL_RECURSIVE_CALL")
    private tailrec fun getMultilineComment(curr: Int): Int {
        when (at(curr)) {
            eofChar -> return curr + 1

            '*' -> if (at(curr + 1) == '/')
                return curr + 2

            '/' -> if (at(curr + 1) == '*')
                return getMultilineComment(getMultilineComment(curr + 2))
        }

        return getMultilineComment(curr + 1)
    }

    private tailrec fun getString(curr: Int): GreenEdge = when (at(curr)) {
        eofChar -> SyntaxType.UnclosedString.edge(code, pos, curr)
        '\"' -> SyntaxType.StringLiteral.edge(code, pos, curr + 1)
        else -> getString(curr + 1)
    }

    private fun isWhitespace(c: Char) = when (c) {
        '\n', eofChar -> false
        ' ', '\t', '\r', '\u000C', '\u2B7F' -> true
        else -> c.isWhitespace()
    }

    private tailrec fun getWhitespace(curr: Int): GreenEdge =
        if (isWhitespace(at(curr))) getWhitespace(curr + 1)
        else SyntaxType.Whitespace.edge(code, pos, curr)

    private fun isIdentBody(c: Char): Boolean = when (c) {
        '_', '\'', in 'a'..'z', in 'A'..'Z', in '0'..'9' -> true
        else -> c.isLetterOrDigit()
    }

    private tailrec fun getIdentifier(curr: Int): GreenEdge =
        if (isIdentBody(at(curr))) getIdentifier(curr + 1)
        else code.substring(pos, curr).matchIdentifier()

    private fun String.matchIdentifier() = when (this) {
        "fun" -> BaseEdges.funKeyword
        else -> SyntaxType.Identifier.edge(this)
    }

    fun move() {
        pos += current.length
        current = edgeAt(pos)
    }
}