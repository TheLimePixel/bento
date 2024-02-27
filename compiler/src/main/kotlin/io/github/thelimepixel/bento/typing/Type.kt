package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemPath

sealed interface Type {
    val accessType: Type
}

data class PathType(val path: ItemPath) : Type {
    override fun toString(): String = path.toString()
    override val accessType: Type
        get() = this
}

data class FunctionType(val paramTypes: List<Type>, val returnType: Type) : Type {
    override fun toString(): String = "(${paramTypes.joinToString(", ")}) -> $returnType"
    override val accessType: Type
        get() = returnType.accessType
}

fun HIR.Def.type(): Type = when (this) {
    is HIR.FunctionLikeDef -> FunctionType(
        paramTypes = params.map { it.type.toType() ?: BuiltinTypes.nothing },
        returnType = PathType(this.returnType?.type ?: BuiltinRefs.unit)
    )
    is HIR.ConstantDef -> FunctionType(emptyList(),  PathType(this.type?.type ?: BuiltinRefs.unit))
}

fun HIR.TypeRef?.toType(): PathType? = this?.type?.let { PathType(it) }