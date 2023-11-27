package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef

interface TypingContext {
    fun signatureOf(ref: ItemRef): FunctionSignature
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionSignature(listOf(BuiltinRefs.string), BuiltinRefs.unit)
    private val errorSignature = FunctionSignature(listOf(BuiltinRefs.nothing), BuiltinRefs.nothing)

    override fun signatureOf(ref: ItemRef): FunctionSignature = when (ref) {
        BuiltinRefs.println -> printlnSig
        else -> errorSignature
    }
}

class ChildTypingContext(
    private val parent: TypingContext,
    private val map: Map<ItemRef, FunctionSignature>
) : TypingContext {
    override fun signatureOf(ref: ItemRef): FunctionSignature =
        map[ref] ?: parent.signatureOf(ref)
}