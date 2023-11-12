package io.github.thelimepixel.bento.parsing

private typealias P = Parser
private typealias ST = SyntaxType

class BentoParsing {
    fun parseFIle(code: String) = parser(Lexer(code), ST.File) { handleFile() }

    private fun P.handleFile() {
        when (current) {
            ST.EOF -> return
            ST.FunKeyword -> handleFunction()
            else -> handleError()
        }
        handleFile()
    }

    private fun P.handleError() {
        pushWrapped(ST.Unknown)
    }

    private fun P.handleFunction() = node(ST.FunDef) {
        push()  // fun keyword
        expectIdentifier()
        expectParamList()
        expectScopeExpr()
    }

    private fun P.expectIdentifier() {
        if (!consume(ST.Identifier)) handleError()
    }

    private fun P.expectParamList() {
        if (!at(ST.LParen)) {
            handleError()
            return
        }

        node(ST.ParamList) {
            push()  // (
            if (!consume(ST.RParen)) handleError()
        }
    }

    private fun P.expectScopeExpr() {
        if (!at(ST.LBrace)) {
            handleError()
            return
        }

        node(ST.ScopeExpr) {
            push()  // {
            handleExpressionScope()
        }
    }

    private tailrec fun P.handleExpressionScope(): Unit = when (current) {
        ST.EOF -> handleError()
        ST.RBrace -> push()
        else -> {
            expectTerm()
            handleExpressionScope()
        }
    }

    private fun P.expectBaseTerm() = when (current) {
        ST.StringLiteral -> push()
        ST.Identifier -> push()
        else -> handleError()
    }

    private fun P.handleCall() = nestLast(ST.CallExpr) {
        node(ST.ArgList) {
            push()  // (
            handleArgList()
        }
    }

    private fun P.handleParenListEnd(): Boolean {
        when (current) {
            ST.EOF -> handleError()
            ST.RBrace -> handleError()
            ST.RParen -> push()
            else -> return false
        }
        return true
    }

    private fun P.expectConsume(type: SyntaxType) {
        if (!consume(type)) handleError()
    }

    private tailrec fun P.handleArgList() {
        if (handleParenListEnd()) return
        expectTerm()
        if (handleParenListEnd()) return
        expectConsume(ST.Comma)
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