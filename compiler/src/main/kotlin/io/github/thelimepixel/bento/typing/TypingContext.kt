package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef
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

class ChildTypingContext(
    private val parent: TypingContext,
    private val map: Map<Ref, Type>
) : TypingContext {
    override fun typeOf(ref: Ref): Type =
        map[ref] ?: parent.typeOf(ref)
}