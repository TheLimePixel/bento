package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.ast.BaseSets
import io.github.thelimepixel.bento.ast.GreenNode
import io.github.thelimepixel.bento.ast.SyntaxSet
import io.github.thelimepixel.bento.ast.SyntaxType

private typealias P = Parser
private typealias ST = SyntaxType

data class Parse(val node: GreenNode, val errors: List<ParseError>)

interface Parsing {
    fun parseFile(code: String): Parse
}

class BentoParsing : Parsing {
    override fun parseFile(code: String): Parse {
        val errorCollector = ParseErrorCollector()
        val node = parser(errorCollector, Lexer(code, errorCollector), ST.File) {
            parseImportStatement()
            handleFile()
        }
        return Parse(node, errorCollector.errors())
    }

    private fun P.handleFile() {
        when (current) {
            ST.EOF -> return
            ST.DefKeyword -> handleFunctionLike()
            ST.LetKeyword -> handleTopLevelLet()
            ST.DataKeyword -> handleTypeDef()
            else -> errorNode(ParseErrorKind.ExpectedDeclaration)
        }
        handleFile()
    }

    private fun P.handleTypeDef(): ParseResult = node(ST.TypeDef) {
        push()      // data keyword
        expectIdentifier(ST.Name).then { parseConstructor() }
    }

    private fun P.parseConstructor(): ParseResult {
        if (!at(ST.LParen)) return ParseResult.Pass

        return node(ST.Constructor) {
            push()      // lParen
            handleParenthesizedList(BaseSets.paramListRecoverySet) { expectField() }
        }
    }

    private fun P.expectField(): ParseResult = if (consume(ST.MutKeyword)) nestLast(ST.Field) {
        expectIdentifier(ST.Name).then { expectTypeAnnotation() }
    } else expectIdentifier(ST.Name).then {
        nestLast(ST.Field) { expectTypeAnnotation() }
    }

    private fun P.parseImportStatement(): ParseResult =
        if (at(ST.ImportKeyword)) node(ST.ImportStatement) {
            push()      // ImportKeyword
            expectImportBlock()
        } else ParseResult.Pass

    private fun P.expectImportBlock(): ParseResult {
        if (!at(ST.LBrace))
            return handleError(ParseErrorKind.ExpectedImportBlock)

        return node(ST.ImportBlock) {
            push()      // LBrace
            handleBracedList(BaseSets.baseRecoverySet) { expectImportPath() }
        }
    }

    private fun P.expectImportPath(): ParseResult =
        expectIdentifier(ST.NameRef).then {
            nestLast(ST.ImportPath) { handlePath() }
        }

    private fun P.handleError(type: ParseErrorKind): ParseResult =
        if (current in BaseSets.baseRecoverySet) pushError(type) else errorNode(type)


    private fun P.handleFunctionLike() = node(ST.FunDef) {
        push()  // def keyword
        expectIdentifier(ST.Name)
        parseParamList()
        parseTypeAnnotation()
        expectEqExpression()
    }

    private fun P.parseTypeAnnotation(): ParseResult =
        if (at(SyntaxType.Colon)) node(SyntaxType.TypeAnnotation) {
            push()  // colon
            expectPath()
        } else ParseResult.Pass

    private fun P.expectTypeAnnotation(): ParseResult =
        if (parseTypeAnnotation() == ParseResult.Success) ParseResult.Success
        else handleError(ParseErrorKind.ExpectedTypeAnnotation)

    private fun P.expectIdentifier(type: SyntaxType): ParseResult =
        if (at(BaseSets.identifiers)) pushWrapped(type)
        else handleError(ParseErrorKind.ExpectedIdentifier)

    private fun P.parseParamList(): ParseResult =
        if (at(ST.LParen)) node(ST.ParamList) {
            push()  // (
            handleParenthesizedList(BaseSets.paramListRecoverySet) { expectParam() }
        } else ParseResult.Pass

    private fun P.expectPattern(): ParseResult = when (current) {
        ST.Wildcard -> pushWrapped(ST.WildcardPattern)
        ST.MutKeyword -> node(ST.MutPattern) {
            push()      // mut keyword
            expectPattern()
        }

        in BaseSets.identifiers -> pushWrapped(ST.IdentPattern)
        else -> handleError(ParseErrorKind.ExpectedPattern)
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


    private fun P.expectEqExpression(): ParseResult =
        if (consume(ST.Equals)) expectTerm()
        else handleError(ParseErrorKind.ExpectedEquals)

    private fun P.handleLetExpr(): ParseResult = node(ST.LetExpr) {
        push()  // let keyword
        expectPattern()
            .then { parseTypeAnnotation() }
            .then { expectEqExpression() }
    }

    private fun P.handleTopLevelLet(): ParseResult = node(ST.LetDef) {
        push()  // let keyword
        consume(ST.MutKeyword)
        expectIdentifier(ST.Name).then {
            parseTypeAnnotation()
            expectEqExpression()
        }
    }

    private fun P.expectScopeTerm(): ParseResult = when (current) {
        ST.LetKeyword -> handleLetExpr()
        else -> expectTerm()
    }

    private fun P.handleParenthesized(): ParseResult = node(ST.ParenthesizedExpr) {
        push()  // (
        expectTerm().then {
            if (consume(SyntaxType.RParen)) ParseResult.Success
            else handleError(ParseErrorKind.ExpectedClosedParen)
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
            in BaseSets.identifiers -> node(ST.PathExpr) {
                pushWrapped(ST.NameRef)
                handlePath()
            }

            else -> handleError(ParseErrorKind.ExpectedExpression)
        }

    private fun P.expectPath(): ParseResult =
        expectIdentifier(ST.NameRef).then { handlePath() }

    private fun P.handlePath(): ParseResult {
        while (at(ST.ColonColon)) {
            nestLast(ST.Path) {
                push()  // ::
                expectIdentifier(ST.NameRef).then { nestLast(ST.PathSegment) }
            }.ifNotPassing { return it }
        }
        return ParseResult.Success
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

        in recoverySet -> pushError(ParseErrorKind.ExpectedCommaOrClosedParen)

        else -> ParseResult.Pass
    }

    private fun P.handleBracedListEnd(recoverySet: SyntaxSet): ParseResult = when (current) {
        ST.RBrace -> {
            push()
            ParseResult.Success
        }

        in recoverySet -> pushError(ParseErrorKind.ExpectedCommaOrClosedBrace)

        else -> ParseResult.Pass
    }

    private inline fun P.handleParenthesizedList(recoverySet: SyntaxSet, expectFn: P.() -> ParseResult): ParseResult {
        while (true) {
            handleParenthesizedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            expectFn().ifIs(ParseResult.Recover) { return it }
            handleParenthesizedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            if (!consume(ST.Comma) && !seenNewline) pushError(ParseErrorKind.ExpectedCommaOrClosedParen)
        }
    }

    private inline fun P.handleBracedList(recoverySet: SyntaxSet, expectFn: P.() -> ParseResult): ParseResult {
        while (true) {
            handleBracedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            expectFn().ifIs(ParseResult.Recover) { return it }
            handleBracedListEnd(recoverySet).ifNot(ParseResult.Pass) { return it }
            if (!consume(ST.Comma) && !seenNewline) pushError(ParseErrorKind.ExpectedCommaOrClosedBrace)
        }
    }

    private fun P.handleAccess(): ParseResult = nestLast(ST.AccessExpr) {
        push()      // dot
        expectIdentifier(ST.NameRef)
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