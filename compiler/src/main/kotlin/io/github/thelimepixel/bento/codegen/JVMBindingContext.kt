package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.FunctionType
import io.github.thelimepixel.bento.typing.Type
import io.github.thelimepixel.bento.typing.TypingContext

interface JVMBindingContext {
    fun signatureOf(ref: ItemRef): JVMSignature

    fun jvmTypeOf(ref: Type): String

    fun localId(ref: LocalRef): Int
}

class TopLevelJVMBindingContext(
    private val printlnFilePath: ItemPath,
    private val stringJVMType: String,
    private val unitJVMType: String,
    private val nothingJVMType: String,
    private val typingContext: TypingContext,
) : JVMBindingContext {
    override fun signatureOf(ref: ItemRef): JVMSignature {
        val path = when (ref) {
            BuiltinRefs.println -> printlnFilePath
            else -> ref.parent.let { it.copy(name = it.name + "Bt") }
        }

        return JVMSignature(path.toJVMPath(), ref.name, mapSignature(typingContext.typeOf(ref) as FunctionType))
    }

    override fun jvmTypeOf(ref: Type): String = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> TODO("Unsupported type")
    }

    private fun mapSignature(signature: FunctionType): String =
        "(${signature.paramTypes.joinToString("") { jvmTypeOf(it) }})${jvmTypeOf(signature.returnType)}"

    override fun localId(ref: LocalRef): Int = error("Unexpected call")
}

class LocalJVMBindingContext(
    private val parent: JVMBindingContext,
    private val localMap: Map<LocalRef, Int>
): JVMBindingContext {
    override fun jvmTypeOf(ref: Type): String = parent.jvmTypeOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature = parent.signatureOf(ref)

    override fun localId(ref: LocalRef): Int = localMap[ref]!!
}