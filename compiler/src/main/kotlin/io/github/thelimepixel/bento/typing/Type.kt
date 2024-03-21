package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*

sealed interface Type {
    val accessType: PathType
    val isSingleton: Boolean
}

data class PathType(val ref: ItemRef) : Type {
    override fun toString(): String = ref.toString()
    override val accessType: PathType
        get() = this

    override val isSingleton: Boolean
        get() = ref.type == ItemType.SingletonType
}

data class FunctionType(val paramTypes: List<PathType>, val returnType: PathType) : Type {
    override fun toString(): String = "(${paramTypes.joinToString(", ")}) -> $returnType"
    override val accessType: PathType
        get() = returnType

    override val isSingleton: Boolean
        get() = false
}

fun HIR.Def.type(defRef: ItemRef): Type = when (this) {
    is HIR.TypeDef -> PathType(defRef)
    is HIR.FunctionLikeDef -> FunctionType(
        paramTypes = params?.map { it.type.toType() ?: BuiltinTypes.nothing } ?: emptyList(),
        returnType = PathType(this.returnType?.type?.let { it.binding.of as ItemRef } ?: BuiltinRefs.unit)
    )
    is HIR.LetDef -> PathType(this.type?.type?.let { it.binding.of as ItemRef } ?: BuiltinRefs.nothing)
    is HIR.Field -> this.type?.toType() ?: BuiltinTypes.nothing
}

fun HIR.TypeRef?.toType(): PathType? = this?.type?.let { PathType(it.binding.of as ItemRef) }