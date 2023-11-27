package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.typing.FunctionSignature
import io.github.thelimepixel.bento.typing.TypingContext

interface JVMBindingContext {
    fun signatureFor(ref: ItemRef): JVMSignature

    fun jvmTypeOf(ref: ItemPath): String
}

class TopLevelJVMBindingContext(
    private val printlnFilePath: ItemPath,
    private val stringJVMType: String,
    private val unitJVMType: String,
    private val nothingJVMType: String,
    private val typingContext: TypingContext,
) : JVMBindingContext {
    override fun signatureFor(ref: ItemRef): JVMSignature {
        val path = when (ref) {
            BuiltinRefs.println -> printlnFilePath
            else -> ref.parent.let { it.copy(name = it.name + "Bt") }
        }

        return JVMSignature(path.toJVMPath(), ref.name, mapSignature(typingContext.signatureOf(ref)))
    }

    override fun jvmTypeOf(ref: ItemPath): String = when (ref) {
        BuiltinRefs.string -> stringJVMType
        BuiltinRefs.unit -> unitJVMType
        BuiltinRefs.nothing -> nothingJVMType
        else -> TODO("Unsupported type")
    }

    private fun mapSignature(signature: FunctionSignature): String =
        "(${signature.paramTypes.joinToString("") { jvmTypeOf(it) }})${jvmTypeOf(signature.returnType)}"
}