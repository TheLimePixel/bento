package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.FunctionRef
import io.github.thelimepixel.bento.binding.PackageRef

interface JVMBindingContext {
    fun signatureFor(ref: FunctionRef): JVMSignature
}

class TopLevelJVMBindingContext(private val printlnSignature: JVMSignature): JVMBindingContext {

    override fun signatureFor(ref: FunctionRef): JVMSignature = when (ref) {
        FunctionRef.Special.println -> printlnSignature
        is FunctionRef.Node -> JVMSignature(ref.module.toJVMPath(), ref.name, "()V")
    }
}