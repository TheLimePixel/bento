package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.utils.EmptyIterator

sealed interface GreenNode {
    val type: SyntaxType
    val length: Int
    val content: String
    val childCount: Int
    fun childIterator(): Iterator<GreenChild>
    fun child(index: Int): GreenChild
    fun childOrNull(index: Int): GreenChild? =
        if (index in 0..<childCount) child(index)
        else null
}

data class GreenEdge(override val type: SyntaxType, override val content: String) : GreenNode {
    override val length: Int
        get() = content.length

    override val childCount: Int
        get() = 0

    override fun childIterator(): Iterator<GreenChild> = EmptyIterator

    override fun child(index: Int): GreenChild = error("Tried to access child of edge node")
}

data class GreenChild(val offset: Int, val node: GreenNode)
data class GreenBranch(override val type: SyntaxType, override val length: Int, val children: List<GreenChild>) : GreenNode {
    override val content: String
        get() = children.joinToString { it.node.content }

    override val childCount: Int
        get() = children.size

    override fun childIterator(): Iterator<GreenChild> = children.iterator()

    override fun child(index: Int): GreenChild = children[index]
}