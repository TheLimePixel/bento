package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.ast.BaseEdges
import io.github.thelimepixel.bento.ast.GreenEdge
import io.github.thelimepixel.bento.ast.SyntaxType
import io.github.thelimepixel.bento.ast.edge

private const val eofChar = Char.MAX_VALUE

class Lexer(private val code: String, private val errorCollector: ParseErrorCollector) {
    var pos: Int = 0
        private set
    var current: GreenEdge = edgeAt(pos)
        private set

    private fun pushError(kind: ParseErrorKind, start: Int, inclusiveEnd: Int) {
        errorCollector.push(kind,start, inclusiveEnd + 1)
    }

    private fun at(index: Int): Char =
        if (index in code.indices) code[index] else eofChar

    private fun edgeAt(index: Int): GreenEdge = when (val char = at(index)) {
        eofChar -> BaseEdges.eof
        '(' -> BaseEdges.lParen
        ')' -> BaseEdges.rParen
        '{' -> BaseEdges.lBrace
        '}' -> BaseEdges.rBrace
        ',' -> BaseEdges.comma
        '.' -> BaseEdges.dot
        ':' -> getColon(index + 1)
        '=' -> BaseEdges.eq
        '\n' -> BaseEdges.nlN
        '\r' -> if (at(index + 1) == '\n') BaseEdges.nlRN else BaseEdges.nlR
        '`' -> getRawIdentifier(index + 1)
        '/' -> getSlash(index + 1)
        ' ', '\t', '\u000C', '\u2B7F' -> getWhitespace(index + 1)
        in 'a'..'z', in 'A'..'Z' -> getIdentifierOrKeyword(index + 1)
        '_' -> getUnderscore(index + 1)
        '\"' -> getString(index + 1)
        else -> when {
            char.isLetter() -> getIdentifierOrKeyword(index + 1)
            char.isWhitespace() -> getWhitespace(index + 1)
            else -> {
                pushError(ParseErrorKind.UnknownSymbol, index, index)
                SyntaxType.Unknown.edge(char.toString())
            }
        }
    }

    private fun getColon(curr: Int) = when (at(curr)) {
        ':' -> BaseEdges.colonColon
        else -> BaseEdges.colon
    }

    private tailrec fun getUnderscore(curr: Int): GreenEdge = when (val char = at(curr)) {
        '_' -> getUnderscore(curr + 1)
        else ->
            if (isIdentBody(char)) getIdentifierOrKeyword(curr + 1)
            else SyntaxType.Wildcard.edge(code, pos, curr)
    }

    private fun getSlash(curr: Int): GreenEdge = when (at(curr)) {
        '/' -> getLineComment(curr + 1)

        '*' -> {
            val endPos = getMultilineComment(curr + 1)
            if (endPos > code.length) {
                pushError(ParseErrorKind.UnclosedComment, code.length, code.length)
                SyntaxType.MultiLineComment.edge(code, pos, code.length)
            } else {
                SyntaxType.MultiLineComment.edge(code, pos, endPos)
            }
        }

        else -> BaseEdges.loneSlash
    }

    private tailrec fun getLineComment(curr: Int): GreenEdge = when (at(curr)) {
        eofChar, '\n', '\r' -> SyntaxType.LineComment.edge(code, pos, curr)
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
        eofChar -> {
            pushError(ParseErrorKind.UnclosedString, curr, curr)
            SyntaxType.StringLiteral.edge(code, pos, curr)
        }

        '\"' -> SyntaxType.StringLiteral.edge(code, pos, curr + 1)
        else -> getString(curr + 1)
    }

    private fun isWhitespace(c: Char) = when (c) {
        '\n', '\r', eofChar -> false
        ' ', '\t', '\u000C', '\u2B7F' -> true
        else -> c.isWhitespace()
    }

    private tailrec fun getWhitespace(curr: Int): GreenEdge =
        if (isWhitespace(at(curr))) getWhitespace(curr + 1)
        else SyntaxType.Whitespace.edge(code, pos, curr)

    private fun isIdentBody(c: Char): Boolean = when (c) {
        '\'', '_', in 'a'..'z', in 'A'..'Z', in '0'..'9' -> true
        else -> c.isLetterOrDigit()
    }

    private tailrec fun getIdentifierOrKeyword(curr: Int): GreenEdge = when (val char = at(curr)) {
        '_' ->
            if (at(curr + 1) == '=') SyntaxType.StandardIdentifier.edge(code, pos, curr + 2)
            else getIdentifier(curr + 1)

        in 'a'..'z' ->
            getIdentifierOrKeyword(curr + 1)

        else ->
            if (isIdentBody(char)) getIdentifier(curr + 1)
            else code.substring(pos, curr).matchIdentifier()
    }

    private tailrec fun getIdentifier(curr: Int): GreenEdge {
        val char = at(curr)

        return when {
            char == '_' ->
                if (at(curr + 1) == '=') SyntaxType.StandardIdentifier.edge(code, pos, curr + 2)
                else getIdentifier(curr + 1)

            isIdentBody(char) ->
                getIdentifier(curr + 1)

            else ->
                code.substring(pos, curr).matchIdentifier()
        }
    }

    private tailrec fun getRawIdentifier(curr: Int): GreenEdge = when (at(curr)) {
        '`' -> SyntaxType.BacktickedIdentifier.edge(code, pos, curr + 1)
        eofChar -> {
            pushError(ParseErrorKind.UnclosedRawIdentifier, curr, curr)
            SyntaxType.BacktickedIdentifier.edge(code, pos, curr)
        }

        else -> getRawIdentifier(curr + 1)
    }

    private fun String.matchIdentifier(): GreenEdge = when (this) {
        "def" -> BaseEdges.defKeyword
        "let" -> BaseEdges.letKeyword
        "import" -> BaseEdges.importKeyword
        "data" -> BaseEdges.dataKeyword
        "mut" -> BaseEdges.mutKeyword
        else -> SyntaxType.StandardIdentifier.edge(this)
    }

    fun move() {
        pos += current.length
        current = edgeAt(pos)
    }
}