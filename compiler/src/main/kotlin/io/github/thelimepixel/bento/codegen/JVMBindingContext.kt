package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef

interface JVMBindingContext {
    fun signatureFor(ref: ItemRef): JVMSignature
}

class TopLevelJVMBindingContext(private val printlnSignature: JVMSignature) : JVMBindingContext {

    override fun signatureFor(ref: ItemRef): JVMSignature = when (ref) {
        BuiltinRefs.println -> printlnSignature
        else -> JVMSignature(ref.pack.toJVMPath(), ref.name, "()V")
    }
}