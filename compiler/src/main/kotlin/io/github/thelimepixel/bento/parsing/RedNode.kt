package io.github.thelimepixel.bento.parsing

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

    val length: Int
        get() = green.length

    val childCount: Int
        get() = green.childCount

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

    fun child(index: Int) = green.child(index).wrap()

    fun childOrNull(index: Int) = green.childOrNull(index)?.wrap()

    private class ChildIterator(
        private val red: RedNode,
        private val backing: Iterator<GreenChild>
    ) : Iterator<RedNode> {
        override fun hasNext(): Boolean = backing.hasNext()

        override fun next(): RedNode = with(red) { backing.next().wrap() }
    }

    fun childIterator(): Iterator<RedNode> = ChildIterator(this, green.childIterator())
    override fun childSequence(): Sequence<RedNode> = childIterator().asSequence()
    fun revChildIterator(): Iterator<RedNode> = ChildIterator(this, green.revChildIterator())
    fun revChildSequence(): Sequence<RedNode> = revChildIterator().asSequence()
}

fun GreenNode.toRedRoot() = RedNode(null, this, 0)