package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.*
import java.util.*

interface JVMBindingContext {
    fun signatureOf(ref: ItemRef): JVMSignature

    fun jvmClassOf(ref: PathType): String

    fun jvmTypeOf(ref: PathType): String = "L${jvmClassOf(ref)};"

    fun localId(ref: LocalRef): Int
}

private fun JVMBindingContext.mapType(type: Type, returnType: Boolean): String = when (type) {
    is PathType -> if (returnType && type.isSingleton) "V" else jvmTypeOf(type)
    is FunctionType -> "(${type.paramTypes.joinToString("") { mapType(it, false) }})${mapType(type.returnType, true)}"
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
        mapType(typingContext.typeOf(BuiltinRefs.println), false)
    )

    override fun signatureOf(ref: ItemRef): JVMSignature =
        if (ref == BuiltinRefs.println) printlnSignature
        else error("Unexpected reference: Expected println, got $ref")


    override fun jvmClassOf(ref: PathType): String = when (ref) {
        BuiltinTypes.string -> stringJVMType
        BuiltinTypes.unit -> unitJVMType
        BuiltinTypes.nothing -> nothingJVMType
        else -> ref.path.path.toJVMPath()
    }

    override fun localId(ref: LocalRef): Int = error("Unexpected call")
}

val ItemRef.jvmName get() = when (type) {
    ItemType.Getter, ItemType.Constant -> "get" + rawName.capitalize()
    ItemType.Setter -> "set" + rawName.capitalize()
    ItemType.SingletonType, ItemType.Function, ItemType.RecordType -> rawName
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

class FileJVMBindingContext(
    private val parent: JVMBindingContext,
    private val typingContext: TypingContext,
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
}

val ItemRef.fileJVMPath: String
    get() = parent.let { it.copy(name = it.rawName + "Bt") }.toJVMPath()
class LocalJVMBindingContext(
    private val parent: JVMBindingContext,
    private val localMap: Map<LocalRef, Int>
) : JVMBindingContext {
    override fun jvmClassOf(ref: PathType): String = parent.jvmClassOf(ref)

    override fun signatureOf(ref: ItemRef): JVMSignature = parent.signatureOf(ref)

    override fun localId(ref: LocalRef): Int = localMap[ref]!!
}