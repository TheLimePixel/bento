package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.parsing.SyntaxType
import io.github.thelimepixel.bento.typing.*
import java.util.*

interface JVMBindingContext {
    fun signatureOf(ref: ItemRef): JVMSignature

    fun jvmTypeOf(ref: Type): String

    fun localId(ref: LocalRef): Int
}

private fun JVMBindingContext.mapType(type: Type): String = when (type) {
    is PathType -> jvmTypeOf(type)
    is FunctionType -> "(${type.paramTypes.joinToString("") { mapType(it) }})${mapType(type.returnType)}"
}

class TopLevelJVMBindingContext(
    printlnFilePath: ItemPath,
    printlnName: String,
    private val stringJVMType: String,
    private val unitJVMType: String,
    private val nothingJVMType: String,
    typingContext: TypingContext,
) : JVMBindingContext {
    private val printlnSignature = JVMSignature(
        printlnFilePath.toJVMPath(),
        printlnName,
        mapType(typingContext.typeOf(BuiltinRefs.println))
    )

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) printlnSignature
        else error("Unexpected reference: Expected println, got $ref")


    override fun jvmTypeOf(ref: Type): String = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> error("Unsupported type: $ref")
    }

    override fun localId(ref: LocalRef): Int = error("Unexpected call")
}

private val ItemRef.jvmName get() = when (this.type) {
    ItemType.Getter -> "get" + this.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    else -> this.name
}

class FileJVMBindingContext(
    private val parent: JVMBindingContext,
    private val typingContext: TypingContext,
) : JVMBindingContext {
    override fun jvmTypeOf(ref: Type): String = parent.jvmTypeOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) parent.signatureOf(ref)
        else JVMSignature(
            parent = ref.parent.let { it.copy(name = it.rawName + "Bt") }.toJVMPath(),
            name = ref.jvmName,
            descriptor = mapType(typingContext.typeOf(ref))
        )

    override fun localId(ref: LocalRef): Int = parent.localId(ref)
}

class LocalJVMBindingContext(
    private val parent: JVMBindingContext,
    private val localMap: Map<LocalRef, Int>
) : JVMBindingContext {
    override fun jvmTypeOf(ref: Type): String = parent.jvmTypeOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature = parent.signatureOf(ref)

    override fun localId(ref: LocalRef): Int = localMap[ref]!!
}