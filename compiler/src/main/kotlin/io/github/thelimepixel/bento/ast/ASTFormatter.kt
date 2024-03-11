package io.github.thelimepixel.bento.ast

import io.github.thelimepixel.bento.utils.Formatter

class ASTFormatter(
    private val ongoingPrefix: String = "│ ",
    private val branchPrefix: String = "├─",
    private val endPrefix: String = "└─"
) : Formatter<GreenNode> {
    private fun format(node: GreenNode, builder: StringBuilder, prefix: StringBuilder) {
        builder.append(node.type)

        if (node.type.dynamic) {
            builder
                .append('(')
                .append(node.content.replace("(\r(\n)?)|\n".toRegex(), "\\\\n"))
                .append(')')
        }

        builder.append('\n')

        val iter = node.childIterator()
        if (!iter.hasNext()) return

        var curr = iter.next().node

        while (iter.hasNext()) {
            builder.append(prefix).append(branchPrefix)
            prefix.append(ongoingPrefix)
            format(curr, builder, prefix)
            prefix.setLength(prefix.length - 2)

            curr = iter.next().node
        }

        builder.append(prefix).append(endPrefix)
        prefix.append("  ")
        format(curr, builder, prefix)
        prefix.setLength(prefix.length - 2)
    }

    override fun format(value: GreenNode): String = StringBuilder().also {
        format(value, it, StringBuilder())
        it.setLength(it.length - 1)
    }.toString()
}