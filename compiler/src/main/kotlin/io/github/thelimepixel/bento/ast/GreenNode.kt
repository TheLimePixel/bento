package io.github.thelimepixel.bento.ast

import io.github.thelimepixel.bento.utils.EmptyIterator

sealed interface GreenNode {
    val type: SyntaxType
    val length: Int
    val content: String
    fun childIterator(): Iterator<GreenChild>
    fun revChildIterator(): Iterator<GreenChild>
    fun childSequence(): Sequence<GreenChild> = childIterator().asSequence()
    fun revChildSequence(): Sequence<GreenChild> = revChildIterator().asSequence()

    fun firstChild(type: SyntaxType): GreenChild? =
        childSequence().firstOrNull { it.type == type }

    fun firstChild(set: SyntaxSet): GreenChild? =
        childSequence().firstOrNull { it.type in set }

    fun lastChild(type: SyntaxType): GreenChild? =
        revChildSequence().firstOrNull { it.type == type }

    fun lastChild(set: SyntaxSet): GreenChild? =
        revChildSequence().firstOrNull { it.type in set }

    val rawContent: String
        get() = content.removeSurrounding("`")
}

data class GreenEdge(override val type: SyntaxType, override val content: String) : GreenNode {
    override val length: Int
        get() = content.length

    override fun childIterator(): Iterator<GreenChild> = EmptyIterator

    override fun revChildIterator(): Iterator<GreenChild> = EmptyIterator
}

data class GreenChild(val offset: Int, val node: GreenNode) {
    val type: SyntaxType get() = node.type
    val content: String get() = node.content
    val rawContent: String get() = node.rawContent
}

data class GreenBranch(
    override val type: SyntaxType,
    override val length: Int,
    val children: List<GreenChild>
) : GreenNode {
    override val content: String
        get() = children.joinToString("") { it.node.content }

    override fun childIterator(): Iterator<GreenChild> = children.iterator()

    override fun revChildIterator(): Iterator<GreenChild> = children.asReversed().iterator()
}