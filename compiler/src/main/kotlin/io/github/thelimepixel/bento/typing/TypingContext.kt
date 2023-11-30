package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.binding.Ref

interface TypingContext {
    fun typeOf(ref: Ref): Type
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionType(listOf(BuiltinTypes.string), BuiltinTypes.unit)
    private val errorSignature = FunctionType(listOf(BuiltinTypes.nothing), BuiltinTypes.nothing)

    override fun typeOf(ref: Ref): Type = when (ref) {
        BuiltinRefs.println -> printlnSig
        else -> errorSignature
    }
}

class FileTypingContext(
    private val parent: TypingContext,
    private val map: Map<ItemRef, Type>
) : TypingContext {
    override fun typeOf(ref: Ref): Type =
        map[ref] ?: parent.typeOf(ref)
}

class FunctionTypingContext(
    private val parent: TypingContext,
    private val map: Map<LocalRef, Type>
) : TypingContext {
    private val locals = mutableMapOf<LocalRef, Type>()
    override fun typeOf(ref: Ref): Type =
        locals[ref] ?: map[ref] ?: parent.typeOf(ref)

    operator fun set(ref: LocalRef, type: Type) {
        locals[ref] = type
    }
}