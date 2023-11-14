package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.GreenNode
import io.github.thelimepixel.bento.parsing.SyntaxType

sealed interface FunctionRef {
    val name: String
    data class Node(val module: PackageRef, override val name: String, val node: GreenNode) : FunctionRef {
        override fun toString(): String = "Node(name=$module::$name)"
    }

    enum class Special : FunctionRef {
        println
    }
}

fun GreenNode.collectFunctions(packRef: PackageRef): List<FunctionRef.Node> = childSequence()
    .map { it.node }
    .filter { it.type == SyntaxType.FunDef }
    .map {
        val name = it.firstChild(SyntaxType.Identifier).content
        FunctionRef.Node(packRef, name, it)
    }
    .toList()

