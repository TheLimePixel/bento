package io.github.thelimepixel.bento.parsing

data class RedNode(val parent: RedNode?, val green: GreenNode, val offset: Int) {
    val type: SyntaxType
        get() = green.type

    val length: Int
        get() = green.length

    val childCount: Int
        get() = green.childCount

    val range: IntRange
        get() = offset..(offset + length)

    private fun wrap(child: GreenChild): RedNode =
        RedNode(this, child.node, offset + child.offset)

    fun child(index: Int) = wrap(green.child(index))

    fun childOrNull(index: Int) = green.childOrNull(index)?.let { wrap(it) }

    fun childIterator(): Iterator<RedNode> = object : Iterator<RedNode> {
            val backing = green.childIterator()

            override fun hasNext(): Boolean = backing.hasNext()

            override fun next(): RedNode = wrap(backing.next())
        }
}