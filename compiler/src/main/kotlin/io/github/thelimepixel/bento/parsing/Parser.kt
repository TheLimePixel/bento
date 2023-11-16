package io.github.thelimepixel.bento.parsing

class Parser internal constructor(private val lexer: Lexer) {
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

    fun pushError(type: ParseError) {
        nodeStack.last().push(GreenError(type, 0, emptyList()))
    }

    inline fun node(type: SyntaxType, build: () -> Unit) {
        pushIgnorables()
        nodeStack.add(NodeBuilder())
        build()
        val node = nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
        nodeStack.last().push(node)
    }

    inline fun errorNode(error: ParseError, build: () -> Unit) {
        pushIgnorables()
        nodeStack.add(NodeBuilder())
        build()
        val node = nodeStack.removeLast().build { length, children -> GreenError(error, length, children) }
        nodeStack.last().push(node)
    }

    inline fun nestLast(type: SyntaxType, build: () -> Unit = {}) {
        val last = nodeStack.last().pop()
        nodeStack.add(NodeBuilder())
        nodeStack.last().push(last)
        build()
        val node = nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
        nodeStack.last().push(node)
    }

    fun pushWrapped(type: SyntaxType) = node(type) { push() }

    fun at(type: SyntaxType): Boolean = lexer.current.type == type

    fun consume(type: SyntaxType): Boolean = at(type).also { if (it) push() }

    fun finish(type: SyntaxType): GreenNode {
        push()
        return nodeStack.removeLast().build { length, children -> GreenBranch(type, length, children) }
    }
}

fun parser(lexer: Lexer, baseType: SyntaxType, fn: Parser.() -> Unit): GreenNode =
    Parser(lexer).apply(fn).finish(baseType)