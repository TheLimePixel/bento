package io.github.thelimepixel.bento.parsing

private typealias P = Parser
private typealias ST = SyntaxType

class BentoParsing {
    fun parseFIle(code: String) = parser(Lexer(code), ST.File) { handleFile() }

    private fun P.handleFile() {
        when (current) {
            ST.EOF -> return
            ST.FunKeyword -> handleFunction()
            else -> handleError(ParseError.ExpectedDeclaration)
        }
        handleFile()
    }

    private fun P.canRecover() = when (current) {
        ST.FunKeyword, ST.EOF, ST.RBrace, ST.LBrace -> true
        else -> false
    }

    private fun P.handleError(type: ParseError) =
        if (canRecover()) pushError(type)
        else errorNode(type) { push() }

    private fun P.handleFunction() = node(ST.FunDef) {
        push()  // fun keyword
        expectIdentifier()
        expectParamList()
        expectScopeExpr()
    }

    private fun P.expectIdentifier() {
        if (!consume(ST.Identifier))
            handleError(ParseError.ExpectedIdentifier)
    }

    private fun P.expectParamList() {
        if (!at(ST.LParen)) {
            handleError(ParseError.ExpectedParameterList)
            return
        }

        node(ST.ParamList) {
            push()  // (
            if (!consume(ST.RParen)) handleError(ParseError.ExpectedCommaOrClosedParen)
        }
    }

    private fun P.parseScopeExpr() = node(ST.ScopeExpr) {
        push()  // {
        handleExpressionScope()
    }

    private fun P.expectScopeExpr() {
        if (!at(ST.LBrace)) {
            handleError(ParseError.ExpectedScope)
            return
        }

        parseScopeExpr()
    }

    private tailrec fun P.handleExpressionScope(): Unit = when (current) {
        ST.EOF -> handleError(ParseError.ExpectedClosedBrace)
        ST.RBrace -> push()
        else -> {
            expectTerm()
            handleExpressionScope()
        }
    }

    private fun P.expectBaseTerm() = when (current) {
        ST.StringLiteral -> push()
        ST.Identifier -> push()
        ST.LBrace -> parseScopeExpr()
        ST.EOF -> {}
        else -> handleError(ParseError.ExpectedExpression)
    }

    private fun P.handleCall() = nestLast(ST.CallExpr) {
        node(ST.ArgList) {
            push()  // (
            handleArgList()
        }
    }

    private fun P.handleParenListEnd(): Boolean {
        when (current) {
            ST.EOF, ST.RBrace -> pushError(ParseError.ExpectedCommaOrClosedParen)
            ST.RParen -> push()
            else -> return false
        }
        return true
    }

    private fun P.expectConsume(type: SyntaxType, error: ParseError) {
        if (!consume(type)) handleError(error)
    }

    private tailrec fun P.handleArgList() {
        if (handleParenListEnd()) return
        expectTerm()
        if (handleParenListEnd()) return
        expectConsume(ST.Comma, ParseError.ExpectedCommaOrClosedParen)
        handleArgList()
    }

    private fun P.handlePostfix() =
        if (!seenNewline && at(ST.LParen)) handleCall()
        else Unit

    private fun P.expectTerm() {
        expectBaseTerm()
        handlePostfix()
    }
}