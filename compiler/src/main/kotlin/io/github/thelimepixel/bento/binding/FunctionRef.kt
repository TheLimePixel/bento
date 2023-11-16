package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

sealed interface FunctionRef {
    val name: String
    data class Node(val path: ItemPath, val node: GreenNode) : FunctionRef {
        override val name: String
            get() = path.name
        override fun toString(): String = "Node(path=$path)"
    }

    enum class Special : FunctionRef {
        println
    }
}

fun GreenNode.collectFunctions(packRef: ItemPath): List<FunctionRef.Node> = childSequence()
    .map { it.node }
    .filter { it.type == SyntaxType.FunDef }
    .map {
        val name = it.firstChild(SyntaxType.Identifier)?.content ?: ""
        FunctionRef.Node(packRef.subPath(name), it)
    }
    .toList()

