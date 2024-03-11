package io.github.thelimepixel.bento.ast

import io.github.thelimepixel.bento.parsing.ParseError
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.Spanned

data class RedNode internal constructor(
    val parent: RedNode?,
    val green: GreenNode,
    val offset: Int
): CodeTree<RedNode, ParseError>, Spanned {
    val content: String
        get() = green.content

    val rawContent: String
        get() = green.rawContent

    val type: SyntaxType
        get() = green.type

    private val length: Int
        get() = green.length

    override val span: IntRange
        get() = offset..(offset + length)

    val ref: ASTRef
        get() = ASTRef(type, span)

    override val error: ParseError?
        get() = green.error

    fun firstChild(type: SyntaxType): RedNode? =
        green.firstChild(type)?.wrap()

    fun firstChild(set: SyntaxSet): RedNode? =
        green.firstChild(set)?.wrap()

    fun lastChild(type: SyntaxType): RedNode? =
        green.lastChild(type)?.wrap()

    fun lastChild(set: SyntaxSet): RedNode? =
        green.lastChild(set)?.wrap()

    private fun GreenChild.wrap(): RedNode =
        RedNode(this@RedNode, this.node, offset + this.offset)

    private class ChildIterator(
        private val red: RedNode,
        private val backing: Iterator<GreenChild>
    ) : Iterator<RedNode> {
        override fun hasNext(): Boolean = backing.hasNext()

        override fun next(): RedNode = with(red) { backing.next().wrap() }
    }

    private fun childIterator(): Iterator<RedNode> = ChildIterator(this, green.childIterator())
    override fun childSequence(): Sequence<RedNode> = childIterator().asSequence()
}

fun GreenNode.toRedRoot() = RedNode(null, this, 0)