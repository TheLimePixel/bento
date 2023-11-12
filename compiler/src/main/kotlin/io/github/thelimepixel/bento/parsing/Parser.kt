package io.github.thelimepixel.bento.parsing

@PublishedApi
internal class NodeBuilder(private val type: SyntaxType) {
    private val nodes: MutableList<GreenNode> = mutableListOf()

    fun push(node: GreenNode) {
        nodes.add(node)
    }

    fun push(list: List<GreenNode>) {
        nodes.addAll(list)
    }

    fun pop(): GreenNode = nodes.removeLast()

    fun build(): GreenBranch {
        if (nodes.isEmpty()) error("Empty node")

        var offset = 0
        val children = nodes.map { raw -> GreenChild(offset, raw).also { offset += raw.length } }

        return GreenBranch(type, offset, children)
    }
}

class Parser internal constructor(private val lexer: Lexer, baseType: SyntaxType) {
    @PublishedApi
    internal val nodeStack: MutableList<NodeBuilder> = mutableListOf(NodeBuilder(baseType))
    private var ignore: MutableList<GreenEdge> = mutableListOf()
    var seenNewline = false
        private set

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

    inline fun node(type: SyntaxType, build: () -> Unit) {
        pushIgnorables()
        nodeStack.add(NodeBuilder(type))
        build()
        val node = nodeStack.removeLast().build()
        nodeStack.last().push(node)
    }

    inline fun nestLast(type: SyntaxType, build: () -> Unit) {
        val last = nodeStack.last().pop()
        nodeStack.add(NodeBuilder(type))
        nodeStack.last().push(last)
        build()
        val node = nodeStack.removeLast().build()
        nodeStack.last().push(node)
    }

    fun pushWrapped(type: SyntaxType) = node(type) { push() }

    fun at(type: SyntaxType): Boolean = lexer.current.type == type

    fun consume(type: SyntaxType): Boolean = at(type).also { if (it) push() }

    fun finish(): GreenNode {
        push()
        return nodeStack.removeLast().build()
    }
}

fun parser(lexer: Lexer, baseType: SyntaxType, fn: Parser.() -> Unit): GreenNode =
    Parser(lexer, baseType).apply(fn).finish()