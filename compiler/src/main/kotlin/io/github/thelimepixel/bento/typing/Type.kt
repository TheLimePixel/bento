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
    is HIR.FunctionDef -> FunctionType(
        paramTypes = params.map { it.type.toPathType() ?: BuiltinTypes.nothing },
        returnType =  returnType?.toPathType() ?: BuiltinTypes.unit
    )
    is HIR.GetterDef ->  returnType?.toPathType() ?: BuiltinTypes.unit
    is HIR.LetDef -> type?.toPathType() ?: BuiltinTypes.unit
    is HIR.Field -> type?.toPathType() ?: BuiltinTypes.nothing
}

fun HIR.TypeRef?.toPathType(): PathType? = this?.type?.let { PathType(it.binding.of as ItemRef) }