package io.github.thelimepixel.bento.errors

import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.Spanned

private fun <Node, Err> collectErrors(
    node: Node,
    list: MutableList<CodeError<Err>>
) where Node : CodeTree<Node, Err>, Node : Spanned, Err : ErrorType {
    node.error?.let { if (!it.ignore) list.add(CodeError(it, node.span)) }
    node.childSequence().forEach { collectErrors(it, list) }
}

fun <Node, Err> collectErrors(node: Node): List<CodeError<Err>>
        where Node : CodeTree<Node, Err>, Node : Spanned, Err : ErrorType =
    mutableListOf<CodeError<Err>>().also { collectErrors(node, it) }