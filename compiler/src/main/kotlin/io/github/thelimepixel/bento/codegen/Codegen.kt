package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.PathType
import io.github.thelimepixel.bento.typing.THIR
import io.github.thelimepixel.bento.typing.toType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.math.max

private typealias JC = JVMBindingContext

interface Codegen {
    fun generate(
        file: SubpackageRef,
        items: List<ItemRef>,
        fileContext: JC,
        hirMap: Map<ItemRef, HIR.Def>,
        thirMap: Map<ItemRef, THIR>
    ): List<Pair<String, ByteArray>>
}

class BentoCodegen : Codegen {
    override fun generate(
        file: SubpackageRef,
        items: List<ItemRef>,
        fileContext: JC,
        hirMap: Map<ItemRef, HIR.Def>,
        thirMap: Map<ItemRef, THIR>
    ): List<Pair<String, ByteArray>> {
        val classes = mutableListOf<Pair<String, ByteArray>>()

        val writer = ClassWriter(0)

        createClass(writer, file.toJVMPath() + "Bt")
        writer.visitSource(file.rawName + ".bt", null)

        val constants = mutableListOf<ItemRef>()

        items.forEach { ref ->
            when (val def = hirMap[ref]!!) {
                is HIR.FunctionLikeDef ->
                    fileContext.genFunctionLikeDef(def, thirMap, ref, writer)

                is HIR.ConstantDef -> {
                    fileContext.genConstantGetter(thirMap, ref, writer)
                    constants.add(ref)
                }

                is HIR.TypeDef -> classes.add(ref.toClassname() to fileContext.genType(ref, def))
                is HIR.Field -> Unit
            }
        }

        genStaticInitializer(writer, constants, fileContext, thirMap)

        writer.visitEnd()
        classes.add(file.toClassname() + "Bt" to writer.toByteArray())

        return classes
    }

    private fun createClass(writer: ClassWriter, name: String) {
        writer.visit(
            52,
            Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC,
            name,
            null,
            "java/lang/Object",
            null
        )
    }

    private fun ParentRef.toClassname(): String = this.toJVMPath(".")

    private fun JC.genType(ref: ItemRef, hir: HIR.TypeDef): ByteArray = when (hir) {
        is HIR.SingletonType -> genSingletonType(ref)
        is HIR.RecordType -> genRecordType(hir, ref)
    }

    private fun MethodVisitor.visitSuper() {
        this.visitVarInsn(Opcodes.ALOAD, 0)
        this.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    }

    private fun JC.genRecordType(hir: HIR.RecordType, ref: ItemRef): ByteArray {
        val writer = ClassWriter(0)

        val `class` = ref.toJVMPath()
        val fields = hir.constructor.fields

        createClass(writer, `class`)

        val ctorDescriptor = hir.constructor.descriptor

        val ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", ctorDescriptor, null, null)
        ctor.visitSuper()

        if (fields.isNotEmpty()) {
            ctor.visitVarInsn(Opcodes.ALOAD, 0)
            fields.forEachIndexed { index, field ->
                if (index != fields.lastIndex)
                    ctor.visitInsn(Opcodes.DUP)

                ctor.visitVarInsn(Opcodes.ALOAD, index + 1)
                ctor.visitFieldInsn(
                    Opcodes.PUTFIELD,
                    `class`,
                    field.name.toJVMIdent(),
                    typeOfField(field)
                )
            }
        }
        ctor.visitInsn(Opcodes.RETURN)
        val ctorStackSize = max(fields.size, 2) + 1
        ctor.visitMaxs(ctorStackSize, fields.size + 1)
        ctor.visitEnd()

        fields.forEach { field ->
            val type = typeOfField(field)
            val name = field.name.toJVMIdent()
            writer.visitField(Opcodes.ACC_FINAL + Opcodes.ACC_PRIVATE, name, type, null, null)
            val getter = writer.visitMethod(
                Opcodes.ACC_FINAL + Opcodes.ACC_PUBLIC,
                "get${name.capitalize()}",
                "()$type",
                null,
                null
            )
            getter.visitVarInsn(Opcodes.ALOAD, 0)
            getter.visitFieldInsn(Opcodes.GETFIELD, `class`, name, type)
            getter.visitInsn(Opcodes.ARETURN)
            getter.visitMaxs(1, 1)
            getter.visitEnd()
        }

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun genSingletonType(ref: ItemRef): ByteArray {
        val writer = ClassWriter(0)

        val `class` = ref.toJVMPath()
        val type = "L$`class`;"

        createClass(writer, `class`)

        val ctor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null)
        ctor.visitSuper()
        ctor.visitInsn(Opcodes.RETURN)
        ctor.visitMaxs(1, 1)
        ctor.visitEnd()

        writer.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC, "INSTANCE", type, null, null)

        val staticCtor = writer.visitMethod(Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE, "<clinit>", "()V", null, null)
        staticCtor.visitTypeInsn(Opcodes.NEW, `class`)
        staticCtor.visitInsn(Opcodes.DUP)
        staticCtor.visitMethodInsn(Opcodes.INVOKESPECIAL, `class`, "<init>", "()V", false)
        staticCtor.visitFieldInsn(Opcodes.PUTSTATIC, `class`, "INSTANCE", type)
        staticCtor.visitInsn(Opcodes.RETURN)
        staticCtor.visitMaxs(2, 0)
        staticCtor.visitEnd()

        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun genStaticInitializer(
        writer: ClassWriter,
        constants: List<ItemRef>,
        fileContext: JC,
        thirMap: Map<ItemRef, THIR>
    ) {
        if (constants.isEmpty()) return

        val staticInitWriter = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE, "<clinit>", "()V", null,
            null
        )
        var maxStack = 0
        var locals = 0

        constants.forEach { ref ->
            val type = fileContext.jvmTypeOf(thirMap[ref]!!.type.accessType)
            val isVoid = type == "V"
            if (!isVoid)
                writer.visitField(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                    ref.rawName,
                    type,
                    null,
                    null
                )
            val thir = thirMap[ref]!!
            val info = jvmFunctionInfoOf(thir)
            maxStack = max(maxStack, info.maxStackSize)
            val localContext = LocalJVMBindingContext(fileContext, info.varIds.mapValues { it.value + locals })
            locals += info.maxLocals
            localContext.genExpr(thir, staticInitWriter, isVoid)
            if (!isVoid)
                staticInitWriter.visitFieldInsn(Opcodes.PUTSTATIC, ref.fileJVMPath, ref.rawName, type)
        }

        staticInitWriter.visitInsn(Opcodes.RETURN)
        staticInitWriter.visitMaxs(maxStack, locals)
        staticInitWriter.visitEnd()
    }

    private fun JC.genFunctionLikeDef(
        def: HIR.FunctionLikeDef,
        thirMap: Map<ItemRef, THIR>,
        ref: ItemRef,
        writer: ClassWriter,
    ) {
        val info = jvmFunctionInfoOf(def, thirMap[ref])
        val sig = signatureOf(ref)
        val methodContext = LocalJVMBindingContext(this, info.varIds)
        val methodVisitor = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
            sig.name,
            sig.descriptor,
            null,
            null
        )
        val isVoid = sig.descriptor.endsWith("V")
        methodContext.genExpr(thirMap[ref]!!, methodVisitor, isVoid)
        methodVisitor.visitInsn(if (isVoid) Opcodes.RETURN else Opcodes.ARETURN)
        methodVisitor.visitMaxs(info.maxStackSize, info.maxLocals)
        methodVisitor.visitEnd()
    }

    private fun JC.genConstantGetter(
        thirMap: Map<ItemRef, THIR>,
        ref: ItemRef,
        writer: ClassWriter,
    ) {
        val type = jvmTypeOf(thirMap[ref]!!.type.accessType)
        val methodVisitor = writer.visitMethod(
            Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC,
            ref.jvmName,
            "()$type",
            null,
            null
        )

        if (type == "V") {
            methodVisitor.visitInsn(Opcodes.RETURN)
            methodVisitor.visitMaxs(0, 0)
        } else {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, ref.fileJVMPath, ref.rawName, type)
            methodVisitor.visitInsn(Opcodes.ARETURN)
            methodVisitor.visitMaxs(1, 0)
        }

        methodVisitor.visitEnd()
    }

    private fun JC.genScopeExpr(
        node: THIR.ScopeExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        val statements = node.statements

        if (statements.isEmpty())
            return false

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, methodWriter, true) }
        return genExpr(statements.last(), methodWriter, ignoreOutput)
    }

    private fun MethodVisitor.visitMethodInsn(op: Int, signature: JVMSignature) {
        visitMethodInsn(op, signature.parent, signature.name, signature.descriptor, false)
    }

    private fun JC.genCallExpr(
        node: THIR.CallExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        node.args.forEach { genExpr(it, methodWriter, false) }
        methodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, signatureOf(node.fn))
        val returns = !node.type.isSingleton

        return when {
            returns && ignoreOutput -> {
                methodWriter.visitInsn(Opcodes.POP)
                false
            }

            !ignoreOutput && !returns ->
                genSingletonAccessExpr(node.type.accessType, methodWriter)

            else -> returns
        }
    }

    context(JC)
    private val HIR.Constructor.descriptor
        get() = fields.joinToString("", "(", ")V") { field -> typeOfField(field) }

    private fun JC.typeOfField(ref: ItemRef) =
        jvmTypeOf((hirOf(ref) as? HIR.Field)?.type?.toType() ?: BuiltinTypes.nothing)

    private fun JC.genConstructorCallExpr(
        node: THIR.ConstructorCallExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        methodWriter.visitTypeInsn(Opcodes.NEW, jvmClassOf(node.type))
        if (!ignoreOutput) methodWriter.visitInsn(Opcodes.DUP)
        node.args.forEach { genExpr(it, methodWriter, false) }

        val hir = hirOf(node.type.ref) as HIR.RecordType
        val ctorDesc = hir.constructor.descriptor

        methodWriter.visitMethodInsn(Opcodes.INVOKESPECIAL, jvmClassOf(node.type), "<init>", ctorDesc, false)
        return ignoreOutput
    }

    private fun JC.genAccessExpr(
        node: THIR.LocalAccessExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean {
        if (ignoreOutput) return false

        methodWriter.visitVarInsn(Opcodes.ALOAD, localId(node.binding))
        return true
    }

    private fun JC.genLetExpr(node: THIR.LetExpr, methodWriter: MethodVisitor): Boolean {
        val local = node.local
        genExpr(node.expr, methodWriter, local == null)
        if (local != null) methodWriter.visitVarInsn(Opcodes.ASTORE, localId(local))
        return false
    }

    private fun genStringExpr(node: THIR.StringExpr, methodWriter: MethodVisitor, ignoreOutput: Boolean): Boolean {
        if (ignoreOutput)
            return false

        methodWriter.visitLdcInsn(node.rawContext)
        return true
    }

    private fun JC.genSingletonAccessExpr(
        nodeType: PathType,
        methodWriter: MethodVisitor,
    ): Boolean {
        val type = jvmClassOf(nodeType)
        methodWriter.visitFieldInsn(Opcodes.GETSTATIC, type, "INSTANCE", "L$type;")
        return true
    }

    private fun JC.genExpr(
        node: THIR,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean,
    ): Boolean = when (node) {
        is THIR.StringExpr ->
            genStringExpr(node, methodWriter, ignoreOutput)

        is THIR.CallExpr ->
            genCallExpr(node, methodWriter, ignoreOutput)

        is THIR.ErrorExpr ->
            false

        is THIR.ScopeExpr ->
            genScopeExpr(node, methodWriter, ignoreOutput)

        is THIR.LocalAccessExpr ->
            genAccessExpr(node, methodWriter, ignoreOutput)

        is THIR.LetExpr ->
            genLetExpr(node, methodWriter)

        is THIR.SingletonAccessExpr -> {
            if (!ignoreOutput) genSingletonAccessExpr(node.type.accessType, methodWriter)
            ignoreOutput
        }

        is THIR.LocalAssignmentExpr -> genLocalAssignmentExpr(node, methodWriter)

        is THIR.FieldAccessExpr -> genFieldAccessExpr(node, methodWriter, ignoreOutput)

        is THIR.ConstructorCallExpr -> genConstructorCallExpr(node, methodWriter, ignoreOutput)
    }

    private fun JC.genLocalAssignmentExpr(node: THIR.LocalAssignmentExpr, writer: MethodVisitor): Boolean {
        genExpr(node.value, writer, false)
        writer.visitVarInsn(Opcodes.ASTORE, localId(node.binding))
        return false
    }

    private fun JC.genFieldAccessExpr(
        node: THIR.FieldAccessExpr,
        methodWriter: MethodVisitor,
        ignoreOutput: Boolean
    ): Boolean {
        genExpr(node.on, methodWriter, ignoreOutput)

        if (ignoreOutput) return false

        val getterName = "get${node.field.name.toJVMIdent().capitalize()}"
        val descriptor = "()${jvmTypeOf(node.type)}"
        methodWriter.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL, node.on.type.accessType.ref.toJVMPath(), getterName, descriptor, false
        )

        return true
    }
}