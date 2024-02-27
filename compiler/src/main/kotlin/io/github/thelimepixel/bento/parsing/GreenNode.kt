package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptyIterator

sealed interface GreenNode : CodeTree<GreenChild, ParseError> {
    val type: SyntaxType
    val length: Int
    val content: String
    val childCount: Int
    override val error: ParseError?
    fun childIterator(): Iterator<GreenChild>
    fun revChildIterator(): Iterator<GreenChild>
    override fun childSequence(): Sequence<GreenChild> = childIterator().asSequence()
    fun revChildSequence(): Sequence<GreenChild> = revChildIterator().asSequence()
    fun child(index: Int): GreenChild
    fun childOrNull(index: Int): GreenChild? =
        if (index in 0..<childCount) child(index)
        else null

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
    override val error: ParseError?
        get() = type.error
    override val length: Int
        get() = content.length

    override val childCount: Int
        get() = 0

    override fun childIterator(): Iterator<GreenChild> = EmptyIterator

    override fun revChildIterator(): Iterator<GreenChild> = EmptyIterator

    override fun child(index: Int): GreenChild = error("Tried to access child of edge node")
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
    override val error: ParseError?
        get() = null
    override val content: String
        get() = children.joinToString("") { it.node.content }

    override val childCount: Int
        get() = children.size

    override fun childIterator(): Iterator<GreenChild> = children.iterator()

    override fun revChildIterator(): Iterator<GreenChild> = children.asReversed().iterator()

    override fun child(index: Int): GreenChild = children[index]
}

data class GreenError(
    override val error: ParseError,
    override val length: Int,
    val children: List<GreenChild>
) : GreenNode {
    override val type: SyntaxType
        get() = SyntaxType.Error

    override val content: String
        get() = children.joinToString { it.node.content }

    override val childCount: Int
        get() = children.size

    override fun childIterator(): Iterator<GreenChild> = children.iterator()

    override fun revChildIterator(): Iterator<GreenChild> = children.asReversed().iterator()

    override fun child(index: Int): GreenChild = children[index]
}