package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef

interface TypingContext {
    fun signatureOf(ref: ItemPath): FunctionSignature
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionSignature(listOf(BuiltinRefs.string), BuiltinRefs.unit)
    private val emptySignature = FunctionSignature(emptyList(), BuiltinRefs.unit)

    override fun signatureOf(ref: ItemPath): FunctionSignature = when (ref) {
        BuiltinRefs.println -> printlnSig
        else -> emptySignature
    }
}