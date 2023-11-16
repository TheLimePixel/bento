package io.github.thelimepixel.bento.parsing


@PublishedApi
internal class NodeBuilder {
    @PublishedApi
    internal val nodes: MutableList<GreenNode> = mutableListOf()

    fun push(node: GreenNode) {
        nodes.add(node)
    }

    fun push(list: List<GreenNode>) {
        nodes.addAll(list)
    }

    fun pop(): GreenNode = nodes.removeLast()

    inline fun build(fn: (Int, List<GreenChild>) -> GreenNode): GreenNode {
        var offset = 0
        val children = nodes.map { raw -> GreenChild(offset, raw).also { offset += raw.length } }

        return fn(offset, children)
    }
}