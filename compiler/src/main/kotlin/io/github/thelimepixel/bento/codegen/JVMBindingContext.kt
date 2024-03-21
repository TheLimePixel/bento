package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.FunctionType
import io.github.thelimepixel.bento.typing.PathType
import io.github.thelimepixel.bento.typing.TypingContext

interface JVMBindingContext {
    fun signatureOf(ref: ItemRef): JVMSignature

    fun jvmClassOf(ref: PathType): JVMClass

    fun hirOf(ref: ItemRef): HIR.Def
}

private fun JVMBindingContext.mapType(type: FunctionType): JVMDescriptor {
    val returnType = if (type.returnType.isSingleton) JVMType.Void else jvmTypeOf(type.returnType)
    return JVMDescriptor(type.paramTypes.map { jvmTypeOf(it) }, returnType)
}

class TopLevelJVMBindingContext(
    printlnFilePath: ItemRef,
    printlnName: String,
    private val stringJVMType: ItemRef,
    private val unitJVMType: ItemRef,
    private val nothingJVMType: ItemRef,
    typingContext: TypingContext,
) : JVMBindingContext {
    private val printlnSignature = JVMSignature(
        printlnFilePath.asJVMClass(),
        printlnName.toJVMName(),
        mapType(typingContext.typeOf(BuiltinRefs.println) as FunctionType)
    )

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) printlnSignature
        else error("Unexpected reference: Expected println, got $ref")


    override fun jvmClassOf(ref: PathType): JVMClass = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> ref.ref
    }.asJVMClass()

    override fun hirOf(ref: ItemRef): HIR.Def = error("Missing definition")
}

class FileJVMBindingContext(
    private val parent: JVMBindingContext,
    private val typingContext: TypingContext,
    private val hirMap: Map<ItemRef, HIR.Def>,
) : JVMBindingContext {
    override fun jvmClassOf(ref: PathType): JVMClass = parent.jvmClassOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) parent.signatureOf(ref)
        else JVMSignature(
            parent = ref.parent.asJVMClass(),
            name = ref.jvmName,
            descriptor = mapType(typingContext.typeOf(ref) as FunctionType)
        )

    override fun hirOf(ref: ItemRef): HIR.Def = hirMap[ref] ?: parent.hirOf(ref)
}

fun JVMBindingContext.jvmTypeOf(ref: PathType): JVMType = JVMType.Class(jvmClassOf(ref))