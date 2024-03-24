package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.*

interface JVMBindingContext {
    fun signatureOf(ref: FunctionRef): JVMSignature

    fun jvmClassOf(ref: PathType): JVMClass

    fun hirOf(ref: ItemRef): HIR.Def

    fun typeOf(ref: ItemRef): Type
}

private fun JVMBindingContext.mapType(type: FunctionType): JVMDescriptor {
    val returnType = if (type.returnType.isSingleton) JVMType.Void else jvmTypeOf(type.returnType)
    return JVMDescriptor(type.paramTypes.map { jvmTypeOf(it) }, returnType)
}

class TopLevelJVMBindingContext(
    printlnRef: FunctionRef,
    private val stringJVMType: TypeRef,
    private val unitJVMType: TypeRef,
    private val nothingJVMType: TypeRef,
    typingContext: TypingContext,
) : JVMBindingContext {
    private val printlnSignature = JVMSignature(
        printlnRef.parent.asJVMClass(),
        printlnRef.jvmName,
        mapType(typingContext.typeOf(BuiltinRefs.println) as FunctionType)
    )

    override fun signatureOf(ref: FunctionRef): JVMSignature =
        if (ref == BuiltinRefs.println) printlnSignature
        else error("Unexpected reference: Expected println, got $ref")


    override fun jvmClassOf(ref: PathType): JVMClass = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> ref.ref
    }.asJVMClass()

    override fun typeOf(ref: ItemRef): Type = error("Cannot find type of $ref")

    override fun hirOf(ref: ItemRef): HIR.Def = error("Missing definition")
}

class FileJVMBindingContext(
    private val parent: JVMBindingContext,
    private val typingContext: TypingContext,
    private val hirMap: Map<ItemRef, HIR.Def?>,
) : JVMBindingContext {
    override fun jvmClassOf(ref: PathType): JVMClass = parent.jvmClassOf(ref)

    override fun signatureOf(ref: FunctionRef): JVMSignature =
        if (ref == BuiltinRefs.println) parent.signatureOf(ref)
        else JVMSignature(
            parent = ref.parent.asJVMClass(),
            name = ref.jvmName,
            descriptor = mapType(typeOf(ref) as FunctionType)
        )

    override fun typeOf(ref: ItemRef): Type = typingContext.typeOf(ref)

    override fun hirOf(ref: ItemRef): HIR.Def = hirMap[ref] ?: parent.hirOf(ref)
}

fun JVMBindingContext.jvmTypeOf(type: PathType): JVMType = JVMType.Class(jvmClassOf(type))