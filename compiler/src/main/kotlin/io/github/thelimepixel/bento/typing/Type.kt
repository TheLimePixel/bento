package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemPath

sealed interface Type

data class PathType(val path: ItemPath) : Type {
    override fun toString(): String = path.toString()
}

data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type {
    override fun toString(): String = "(${paramTypes.joinToString(", ")}) -> $returnType"
}

fun HIR.Function.type(): FunctionType = FunctionType(
    paramTypes = params.map { it.type.toType() ?: BuiltinTypes.nothing },
    returnType = PathType(this.returnType?.type ?: BuiltinRefs.unit)
)

fun HIR.TypeRef?.toType(): PathType? = this?.type?.let { PathType(it) }