package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.*
import java.util.*

interface JVMBindingContext {
    fun signatureOf(ref: ItemRef): JVMSignature

    fun jvmClassOf(ref: PathType): String

    fun jvmTypeOf(ref: PathType): String = "L${jvmClassOf(ref)};"

    fun localId(ref: LocalRef): Int

    fun hirOf(ref: ItemRef): HIR.Def
}

private fun JVMBindingContext.mapType(type: Type, returnType: Boolean): String = when (type) {
    is PathType -> if (returnType && type.isSingleton) "V" else jvmTypeOf(type)
    is FunctionType -> "(${type.paramTypes.joinToString("") { mapType(it, false) }})${mapType(type.returnType, true)}"
}

class TopLevelJVMBindingContext(
    printlnFilePath: PackageRef,
    printlnName: String,
    private val stringJVMType: String,
    private val unitJVMType: String,
    private val nothingJVMType: String,
    typingContext: TypingContext,
) : JVMBindingContext {
    private val printlnSignature = JVMSignature(
        printlnFilePath.toJVMPath(),
        printlnName,
        mapType(typingContext.typeOf(BuiltinRefs.println), false)
    )

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) printlnSignature
        else error("Unexpected reference: Expected println, got $ref")


    override fun jvmClassOf(ref: PathType): String = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> ref.ref.toJVMPath()
    }

    override fun localId(ref: LocalRef): Int = error("Unexpected call")

    override fun hirOf(ref: ItemRef): HIR.Def = error("Missing definition")
}

val ItemRef.jvmName
    get() = when (type) {
        ItemType.Getter, ItemType.Constant -> "get" + rawName.capitalize()
        ItemType.Setter -> "set" + rawName.capitalize()
        ItemType.SingletonType, ItemType.Function, ItemType.RecordType, ItemType.Field -> rawName
    }

internal fun String.capitalize() = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

class FileJVMBindingContext(
    private val parent: JVMBindingContext,
    private val typingContext: TypingContext,
    private val hirMap: Map<ItemRef, HIR.Def>,
) : JVMBindingContext {
    override fun jvmClassOf(ref: PathType): String = parent.jvmClassOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) parent.signatureOf(ref)
        else JVMSignature(
            parent = ref.fileJVMPath,
            name = ref.jvmName,
            descriptor = mapType(typingContext.typeOf(ref), false)
        )

    override fun localId(ref: LocalRef): Int = parent.localId(ref)

    override fun hirOf(ref: ItemRef): HIR.Def = hirMap[ref] ?: parent.hirOf(ref)
}

val ItemRef.fileJVMPath: String
    get() = parent.toJVMPath() + "Bt"

class LocalJVMBindingContext(
    private val parent: JVMBindingContext,
    private val localMap: Map<LocalRef, Int>
) : JVMBindingContext {
    override fun jvmClassOf(ref: PathType): String = parent.jvmClassOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature = parent.signatureOf(ref)

    override fun localId(ref: LocalRef): Int = localMap[ref]!!

    override fun hirOf(ref: ItemRef): HIR.Def = parent.hirOf(ref)
}