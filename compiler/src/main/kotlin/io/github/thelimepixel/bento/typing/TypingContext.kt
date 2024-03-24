package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.*

interface TypingContext {
    fun typeOf(ref: Ref): Type
    fun memberOf(type: TypeRef, name: String): Accessor?
    fun hirOf(type: ItemRef): HIR.Def
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionType(listOf(BuiltinTypes.string), BuiltinTypes.unit)
    private val errorSignature = FunctionType(listOf(BuiltinTypes.nothing), BuiltinTypes.nothing)

    override fun typeOf(ref: Ref): Type = when (ref) {
        BuiltinRefs.println -> printlnSig
        else -> errorSignature
    }

    override fun memberOf(type: TypeRef, name: String): Accessor? = null

    override fun hirOf(type: ItemRef): HIR.Def = error("Missing HIR for $type")
}

class FileTypingContext(
    private val parent: TypingContext,
    private val itemTypes: Map<ItemRef, Type>,
    private val hirMap: Map<ItemRef, HIR.Def?>,
    private val astMap: InfoMap,
) : TypingContext {
    override fun typeOf(ref: Ref): Type =
        itemTypes[ref] ?: parent.typeOf(ref)

    override fun memberOf(type: TypeRef, name: String): Accessor? =
        astMap[type]?.accessors?.get(name) ?: parent.memberOf(type, name)

    override fun hirOf(type: ItemRef): HIR.Def = hirMap[type] ?: parent.hirOf(type)
}

class LocalTypingContext(
    private val parent: TypingContext,
) : TypingContext {
    private val locals = mutableMapOf<Ref, Type>()
    override fun typeOf(ref: Ref): Type =
        locals[ref] ?: parent.typeOf(ref)

    operator fun set(ref: Ref, type: Type) {
        locals[ref] = type
    }

    override fun memberOf(type: TypeRef, name: String): Accessor? =
        parent.memberOf(type, name)

    override fun hirOf(type: ItemRef): HIR.Def = parent.hirOf(type)
}

fun TypingContext.typeOf(accessor: Accessor) = when (accessor.type) {
    AccessorType.Get -> typeOf(accessor.of)
    AccessorType.Set -> FunctionType(listOf(typeOf(accessor.of) as PathType), BuiltinTypes.unit)
}