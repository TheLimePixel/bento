package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef

interface TypingContext {
    fun signatureOf(ref: ItemRef): FunctionSignature
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionSignature(listOf(BuiltinRefs.string), BuiltinRefs.unit)
    private val emptySignature = FunctionSignature(emptyList(), BuiltinRefs.unit)

    override fun signatureOf(ref: ItemRef): FunctionSignature = when (ref) {
        BuiltinRefs.println -> printlnSig
        else -> emptySignature
    }
}