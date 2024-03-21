package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.LocalRef
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class MethodWriter internal constructor(private val visitor: MethodVisitor, virtual: Boolean) {
    private var stackCounter = 0
    private var maxStack = 0
    private var maxLocals = if (virtual) 1 else 0
    private val localMapping = mutableMapOf<LocalRef, Int>()

    private fun incrementStack() {
        stackCounter += 1
        maxStack = stackCounter.coerceAtLeast(maxStack)
    }
    fun returnVoid() {
        visitor.visitInsn(Opcodes.RETURN)
    }
    fun addVariable(ref: LocalRef) {
        localMapping[ref] = maxLocals
        maxLocals += 1
    }

    fun addVariables(amount: Int) {
        maxLocals += amount
    }

    fun getLocal(ref: LocalRef) {
        getLocal(localMapping[ref]!!)
    }

    fun setLocal(ref: LocalRef) {
        setLocal(localMapping[ref]!!)
    }

    fun returnLast() {
        stackCounter -= 1
        visitor.visitInsn(Opcodes.ARETURN)
    }

    fun getField(owner: JVMClass, name: JVMName, type: JVMType) {
        visitor.visitFieldInsn(Opcodes.GETFIELD, owner.string, name.string, "$type")
    }

    fun setField(owner: JVMClass, name: JVMName, type: JVMType) {
        stackCounter -= 2
        visitor.visitFieldInsn(Opcodes.PUTFIELD, owner.string, name.string, "$type")
    }

    fun getStatic(owner: JVMClass, name: JVMName, type: JVMType) {
        incrementStack()
        visitor.visitFieldInsn(Opcodes.GETSTATIC, owner.string, name.string, "$type")
    }

    fun setStatic(owner: JVMClass, name: JVMName, type: JVMType) {
        stackCounter -= 1
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, owner.string, name.string, "$type")
    }

    fun getLocal(index: Int) {
        incrementStack()
        visitor.visitVarInsn(Opcodes.ALOAD, index)
    }

    fun setLocal(index: Int) {
        stackCounter -= 1
        visitor.visitVarInsn(Opcodes.ASTORE, index)
    }

    fun callVirtual(owner: JVMClass, name: JVMName, descriptor: JVMDescriptor) {
        stackCounter -= descriptor.parameters.size
        if (descriptor.returnType == JVMType.Void) stackCounter -= 1
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.string, name.string, "$descriptor", false)
    }

    fun callStatic(owner: JVMClass, name: JVMName, descriptor: JVMDescriptor) {
        stackCounter -= descriptor.parameters.size
        if (descriptor.returnType != JVMType.Void) incrementStack()
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.string, name.string, "$descriptor", false)
    }

    fun callVirtualGetter(owner: JVMClass, name: JVMName, type: JVMType) {
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.string, "get${name.capitalize()}", "()$type", false)
    }

    fun callStaticGetter(owner: JVMClass, name: JVMName, type: JVMType) {
        incrementStack()
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.string, "get${name.capitalize()}", "()$type", false)
    }

    fun callVirtualSetter(owner: JVMClass, name: JVMName, type: JVMType) {
        stackCounter -= 2
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.string, "set${name.capitalize()}", "($type)V", false)
    }

    fun callStaticSetter(owner: JVMClass, name: JVMName, type: JVMType) {
        stackCounter -= 1
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.string, "set${name.capitalize()}", "($type)V", false)
    }

    fun duplicateLast() {
        incrementStack()
        visitor.visitInsn(Opcodes.DUP)
    }

    fun instantiate(type: JVMClass) {
        incrementStack()
        visitor.visitTypeInsn(Opcodes.NEW, type.string)
    }

    fun getThis() {
        getLocal(0)
    }

    fun popLast() {
        stackCounter -= 1
        visitor.visitInsn(Opcodes.POP)
    }

    fun callConstructor(owner: JVMClass, descriptor: JVMDescriptor) {
        stackCounter -= descriptor.parameters.size + 1
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner.string, "<init>", "$descriptor", false)
    }

    fun callSuper() {
        getThis()
        callConstructor(JVMClass("java/lang/Object"), emptyJVMDescriptor)
    }

    fun loadString(value: String) {
        incrementStack()
        visitor.visitLdcInsn(value)
    }

    fun finish() {
        visitor.visitMaxs(maxStack, maxLocals)
        visitor.visitEnd()
    }
}