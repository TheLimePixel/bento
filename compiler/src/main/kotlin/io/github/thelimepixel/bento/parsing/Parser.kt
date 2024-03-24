package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.ast.*

class Parser internal constructor(
    private val errorCollector: ParseErrorCollector,
    private val lexer: Lexer,
) {
    @PublishedApi
    internal val nodeStack: MutableList<NodeBuilder> = mutableListOf(NodeBuilder())
    private var ignore: MutableList<GreenEdge> = mutableListOf()
    var seenNewline = false
        private set

    init {
        collectIgnorables()
    }

    val current: SyntaxType get() = lexer.current.type

    private fun collectIgnorables() {
        seenNewline = false

        while (current.ignore) {
            if (current == SyntaxType.Newline) seenNewline = true
            ignore.add(lexer.current)
            lexer.move()
        }
    }

    @PublishedApi
    internal fun pushIgnorables() {
        nodeStack.last().push(ignore)
        ignore.clear()
    }

    fun push() {
        pushIgnorables()
        nodeStack.last().push(lexer.current)
        lexer.move()
        collectIgnorables()
    }

    fun pushError(kind: ParseErrorKind): ParseResult {
        errorCollector.push(kind, lexer.pos, lexer.pos)
        return ParseResult.Recover
    }

    inline fun node(type: SyntaxType, crossinline build: () -> ParseResult): ParseResult {
        pushIgnorables()
        nodeStack.add(NodeBuilder())
        val res = build()
        val node = nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
        nodeStack.last().push(node)
        return res
    }

    fun errorNode(error: ParseErrorKind): ParseResult {
        errorCollector.push(error, lexer.pos, lexer.pos + lexer.current.length)
        pushWrapped(SyntaxType.Error)
        return ParseResult.Failure
    }

    inline fun nestLast(type: SyntaxType, crossinline build: () -> ParseResult = { ParseResult.Success }): ParseResult {
        val last = nodeStack.last().pop()
        nodeStack.add(NodeBuilder())
        nodeStack.last().push(last)
        val res = build()
        val node = nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
        nodeStack.last().push(node)
        return res
    }

    fun pushWrapped(type: SyntaxType) = node(type) {
        push()
        ParseResult.Success
    }

    fun at(type: SyntaxType): Boolean = lexer.current.type == type

    fun at(set: SyntaxSet): Boolean = lexer.current.type in set

    fun consume(type: SyntaxType): Boolean = at(type).also { if (it) push() }

    fun finish(type: SyntaxType): GreenNode {
        push()
        return nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
    }
}

fun parser(errorCollector: ParseErrorCollector, lexer: Lexer, baseType: SyntaxType, fn: Parser.() -> Unit): GreenNode =
    Parser(errorCollector, lexer).apply(fn).finish(baseType)