package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*

sealed interface Type {
    val accessType: PathType
    val isSingleton: Boolean
}

data class PathType(val ref: TypeRef) : Type {
    override fun toString(): String = ref.toString()
    override val accessType: PathType
        get() = this

    override val isSingleton: Boolean
        get() = ref is SingletonTypeRef
}

data class FunctionType(val paramTypes: List<PathType>, val returnType: PathType) : Type {
    override fun toString(): String = "(${paramTypes.joinToString(", ")}) -> $returnType"
    override val accessType: PathType
        get() = returnType

    override val isSingleton: Boolean
        get() = false
}

fun type(ref: ItemRef, hirMap: Map<ItemRef, HIR.Def?>): Type = when (ref) {
    is TypeRef ->
        PathType(ref)

    is FunctionRef -> {
        val hir = hirMap[ref] as HIR.FunctionDef
        FunctionType(
            paramTypes = hir.params.map { it.type.toPathType() ?: BuiltinTypes.nothing },
            returnType = hir.returnType?.toPathType() ?: BuiltinTypes.unit
        )
    }

    is GetterRef ->
        (hirMap[ref] as HIR.GetterDef).returnType?.toPathType() ?: BuiltinTypes.unit

    is StoredPropertyRef ->
        (hirMap[ref] as HIR.LetDef).type?.toPathType() ?: BuiltinTypes.unit

    is FieldRef ->
        (hirMap[ref.parent] as HIR.ProductType).fields[ref.index].type.toPathType() ?: BuiltinTypes.nothing

    is PackageRef -> BuiltinTypes.nothing
}

fun HIR.TypeRef?.toPathType(): PathType? = this?.type?.let { PathType(it.binding.of as TypeRef) }