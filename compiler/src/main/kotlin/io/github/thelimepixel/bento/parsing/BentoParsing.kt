package io.github.thelimepixel.bento.parsing

private typealias P = Parser
private typealias ST = SyntaxType

class BentoParsing {
    fun parseFIle(code: String) = parser(Lexer(code), ST.File) { handleFile() }

    private fun P.handleFile() {
        when (current) {
            ST.EOF -> return
            ST.FunKeyword -> handleFunction()
            else -> errorNode(ParseError.ExpectedDeclaration) { push() }
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
        parseTypeAnnotation()
        expectScopeExpr()
    }

    private fun P.parseTypeAnnotation(): Boolean {
        val canParse = at(SyntaxType.Colon)
        if (canParse) node(SyntaxType.TypeAnnotation) {
            push()  // colon
            expectIdentifier()
        }
        return canParse
    }

    private fun P.expectTypeAnnotation() {
        if (!parseTypeAnnotation()) pushError(ParseError.ExpectedTypeAnnotation)
    }

    private fun P.expectIdentifier() {
        if (!consume(ST.Identifier))
            handleError(ParseError.ExpectedIdentifier)
    }

    private fun P.expectParamList() {
        if (!at(ST.LParen))
            return handleError(ParseError.ExpectedParameterList)

        node(ST.ParamList) {
            push()  // (
            handleParamList()
        }
    }

    private fun P.consumePattern(): Boolean {
        when (current) {
            ST.Identifier -> pushWrapped(ST.IdentPattern)
            ST.Wildcard -> pushWrapped(ST.WildcardPattern)
            else -> return false
        }
        return true
    }

    private fun P.expectParam() {
        if (!consumePattern())
            return handleError(ParseError.ExpectedPattern)

        nestLast(SyntaxType.Param) {
            expectTypeAnnotation()
        }
    }

    private tailrec fun P.handleParamList() {
        if (handleParamListEnd()) return
        expectParam()
        if (handleParamListEnd()) return
        expectConsume(ST.Comma, ParseError.ExpectedCommaOrClosedParen)
        handleParamList()
    }

    private fun P.parseScopeExpr() = node(ST.ScopeExpr) {
        push()  // {
        handleExpressionScope()
    }

    private fun P.expectScopeExpr() {
        if (!at(ST.LBrace))
            return handleError(ParseError.ExpectedScope)

        parseScopeExpr()
    }

    private fun P.expectEqExpression() {
        if (!consume(ST.Equals))
            return pushError(ParseError.ExpectedEquals)

        expectTerm()
    }

    private fun P.handleLetExpr() = node(ST.LetExpr) {
        push()  // let keyword
        if (!consumePattern()) pushError(ParseError.ExpectedPattern)
        parseTypeAnnotation()
        expectEqExpression()
    }

    private tailrec fun P.handleExpressionScope() {
        when (current) {
            ST.EOF -> return handleError(ParseError.ExpectedClosedBrace)
            ST.RBrace -> return push()
            ST.Comma -> push()
            ST.LetKeyword -> handleLetExpr()
            else -> expectTerm()
        }
        handleExpressionScope()
    }

    private fun P.handleParenthesized() = node(ST.ParenthesizedExpr) {
        push()  // (
        expectTerm()
        if (!consume(SyntaxType.RParen)) pushError(ParseError.ExpectedClosedParen)
    }

    private fun P.expectBaseTerm() = when (current) {
        ST.StringLiteral -> push()
        ST.Identifier -> push()
        ST.LBrace -> parseScopeExpr()
        ST.LParen -> handleParenthesized()
        ST.EOF -> {}
        else -> handleError(ParseError.ExpectedExpression)
    }

    private fun P.handleCall() = nestLast(ST.CallExpr) {
        node(ST.ArgList) {
            push()  // (
            handleArgList()
        }
    }

    private fun P.handleParamListEnd(): Boolean {
        when (current) {
            ST.EOF, ST.RBrace, ST.LBrace -> pushError(ParseError.ExpectedCommaOrClosedParen)
            ST.RParen -> push()
            else -> return false
        }
        return true
    }

    private fun P.handleArgListEnd(): Boolean {
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
        if (handleArgListEnd()) return
        expectTerm()
        if (handleArgListEnd()) return
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