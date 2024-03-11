package io.github.thelimepixel.bento.parsing

private typealias P = Parser
private typealias ST = SyntaxType

class BentoParsing {
    fun parseFIle(code: String) = parser(Lexer(code), ST.File) {
        parseImportStatement()
        handleFile()
    }

    private fun P.handleFile() {
        when (current) {
            ST.EOF -> return
            ST.FunKeyword -> handleFunctionLike(ST.FunDef)
            ST.GetKeyword -> handleFunctionLike(ST.GetDef)
            ST.SetKeyword -> handleFunctionLike(ST.SetDef)
            ST.LetKeyword -> handleTopLevelLet()
            ST.DataKeyword -> handleTypeDef()
            else -> errorNode(ParseError.ExpectedDeclaration) { push() }
        }
        handleFile()
    }

    private fun P.handleTypeDef(): ParseResult = node(ST.TypeDef) {
        push()      // data keyword
        expectIdentifier().then { parseConstructor() }
    }

    private fun P.parseConstructor(): ParseResult {
        if (!at(ST.LParen)) return ParseResult.Pass

        return node(ST.Constructor) {
            push()      // lParen
            handleParenthesizedList(BaseSets.paramListRecoverySet) { expectField() }
        }
    }

    private fun P.expectField(): ParseResult =
        expectIdentifier().then {
            nestLast(ST.Field) { expectTypeAnnotation() }
        }

    private fun P.parseImportStatement(): ParseResult =
        if (at(ST.ImportKeyword)) node(ST.ImportStatement) {
            push()      // ImportKeyword
            expectImportBlock()
        } else ParseResult.Pass

    private fun P.expectImportBlock(): ParseResult {
        if (!at(ST.LBrace))
            return handleError(ParseError.ExpectedImportBlock)

        return node(ST.ImportBlock) {
            push()      // LBrace
            handleBracedList(BaseSets.baseRecoverySet) { expectImportPath() }
        }
    }

    private fun P.expectImportPath(): ParseResult =
        expectIdentifier().then {
            nestLast(ST.ImportPath) {
                while (consume(ST.ColonColon)) {
                    expectIdentifier().ifNotPassing { return@nestLast it }
                }
                ParseResult.Success
            }
        }
    private fun P.handleError(type: ParseError): ParseResult =
        if (current in BaseSets.baseRecoverySet) {
            pushError(type)
            ParseResult.Recover
        } else {
            errorNode(type) { push() }
            ParseResult.Failure
        }


    private fun P.handleFunctionLike(type: ST) = node(type) {
        push()  // keyword
        expectIdentifier()
        expectParamList()
        parseTypeAnnotation()
        expectScopeExpr()
    }

    private fun P.parseTypeAnnotation(): ParseResult =
        if (at(SyntaxType.Colon)) node(SyntaxType.TypeAnnotation) {
            push()  // colon
            expectPath()
        } else ParseResult.Pass

    private fun P.expectTypeAnnotation(): ParseResult =
        if (parseTypeAnnotation() == ParseResult.Success) ParseResult.Success
        else handleError(ParseError.ExpectedTypeAnnotation)

    private fun P.expectIdentifier(): ParseResult =
        if (at(BaseSets.identifiers)) pushWrapped(ST.Identifier)
        else handleError(ParseError.ExpectedIdentifier)

    private fun P.expectParamList(): ParseResult =
        if (at(ST.LParen)) node(ST.ParamList) {
            push()  // (
            handleParenthesizedList(BaseSets.paramListRecoverySet) { expectParam() }
        } else handleError(ParseError.ExpectedParameterList)

    private fun P.expectPattern(): ParseResult = when (current) {
        ST.Wildcard -> pushWrapped(ST.WildcardPattern)
        in BaseSets.identifiers -> pushWrapped(ST.IdentPattern)
        else -> handleError(ParseError.ExpectedPattern)
    }

    private fun P.expectParam(): ParseResult =
        expectPattern().then {
            nestLast(SyntaxType.Param) {
                expectTypeAnnotation()
            }
        }

    private fun P.handleScopeExpr(): ParseResult = node(ST.ScopeExpr) {
        push()  // {
        handleBracedList(BaseSets.scopeRecoverySet) { expectScopeTerm() }
    }

    private fun P.expectScopeExpr(): ParseResult =
        if (at(ST.LBrace)) handleScopeExpr()
        else handleError(ParseError.ExpectedScope)


    private fun P.expectEqExpression(): ParseResult =
        if (consume(ST.Equals)) expectTerm()
        else handleError(ParseError.ExpectedEquals)

    private fun P.handleLetExpr(): ParseResult = node(ST.LetExpr) {
        push()  // let keyword
        expectPattern()
            .then { parseTypeAnnotation() }
            .then { expectEqExpression() }
    }

    private fun P.handleTopLevelLet(): ParseResult = node(ST.LetDef) {
        push()  // let keyword
        expectIdentifier()
            .then { parseTypeAnnotation() }
            .then { expectEqExpression() }
    }

    private fun P.expectScopeTerm(): ParseResult = when (current) {
        ST.LetKeyword -> handleLetExpr()
        else -> expectTerm()
    }

    private fun P.handleParenthesized(): ParseResult = node(ST.ParenthesizedExpr) {
        push()  // (
        expectTerm().then {
            if (consume(SyntaxType.RParen)) ParseResult.Success
            else handleError(ParseError.ExpectedClosedParen)
        }
    }

    private fun P.expectBaseTerm(): ParseResult =
        when (current) {
            ST.StringLiteral -> {
                push()
                ParseResult.Success
            }

            ST.LBrace -> handleScopeExpr()
            ST.LParen -> handleParenthesized()
            in BaseSets.identifiers -> {
                pushWrapped(ST.Identifier)
                handlePath()
            }

            else -> handleError(ParseError.ExpectedExpression)
        }

    private fun P.expectPath(): ParseResult =
        expectIdentifier().then { handlePath() }

    private fun P.handlePath(): ParseResult = nestLast(ST.Path) {
        while (consume(ST.ColonColon)) {
            expectIdentifier().ifNotPassing { return@nestLast it }
        }
        ParseResult.Success
    }

    private fun P.handleCall(): ParseResult = nestLast(ST.CallExpr) {
        node(ST.ArgList) {
            push()  // (
            handleParenthesizedList(BaseSets.argListRecoverySet) { expectTerm() }
        }
    }

    private fun P.handleAssignment(): ParseResult = nestLast(ST.AssignmentExpr) {
        push() // equals
        expectTerm()
    }

    private fun P.handleParenthesizedListEnd(recoverySet: SyntaxSet): ParseResult = when (current) {
        ST.RParen -> {
            push()
            ParseResult.Success
        }

        in recoverySet -> {
            pushError(ParseError.ExpectedCommaOrClosedParen)
            ParseResult.Recover
        }

        else -> ParseResult.Pass
    }

    private fun P.handleBracedListEnd(recoverySet: SyntaxSet): ParseResult = when (current) {
        ST.RBrace -> {
            push()
            ParseResult.Success
        }

        in recoverySet -> {
            pushError(ParseError.ExpectedCommaOrClosedBrace)
            ParseResult.Recover
        }

        else -> ParseResult.Pass
    }

    private inline fun P.handleParenthesizedList(recoverySet: SyntaxSet, expectFn: P.() -> ParseResult): ParseResult {
        while (true) {
            handleParenthesizedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            expectFn().ifIs(ParseResult.Recover) { return it }
            handleParenthesizedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            if (!consume(ST.Comma) && !seenNewline) pushError(ParseError.ExpectedCommaOrClosedParen)
        }
    }

    private inline fun P.handleBracedList(recoverySet: SyntaxSet, expectFn: P.() -> ParseResult): ParseResult {
        while (true) {
            handleBracedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            expectFn().ifIs(ParseResult.Recover) { return it }
            handleBracedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            if (!consume(ST.Comma) && !seenNewline) pushError(ParseError.ExpectedCommaOrClosedBrace)
        }
    }

    private fun P.handleAccess(): ParseResult = nestLast(ST.AccessExpr) {
        push()      // dot
        expectIdentifier()
    }

    private tailrec fun P.handlePostfix(): ParseResult {
        val res = when {
            !seenNewline && at(ST.LParen) -> handleCall()
            at(ST.Equals) -> handleAssignment()
            at(ST.Dot) -> handleAccess()
            else -> return ParseResult.Pass
        }
        return if (res.passing) handlePostfix() else res
    }

    private fun P.expectTerm(): ParseResult =
        expectBaseTerm().then { handlePostfix() }
}