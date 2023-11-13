package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.FunctionRef

interface TypingContext {
    fun signatureOf(ref: FunctionRef): FunctionSignature
}

class TopLevelTypingContext : TypingContext {
    private val printlnSig = FunctionSignature(listOf(BentoType.String), BentoType.Unit)
    private val emptySignature = FunctionSignature(emptyList(), BentoType.Unit)

    override fun signatureOf(ref: FunctionRef): FunctionSignature = when (ref) {
        FunctionRef.Special.println -> printlnSig
        else -> emptySignature
    }
}