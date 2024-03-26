package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.*
import io.github.thelimepixel.bento.typing.*

private typealias JC = JVMBindingContext

interface Codegen {
    fun generate(
        file: SubpackageRef,
        items: List<ItemRef>,
        fileContext: JC,
    ): List<Pair<String, ByteArray>>
}

class BentoCodegen : Codegen {
    override fun generate(
        file: SubpackageRef,
        items: List<ItemRef>,
        fileContext: JC,
    ): List<Pair<String, ByteArray>> {
        val classes = mutableListOf<Pair<String, ByteArray>>()

        val writer = ClassWriter(file.asJVMClass())
        val storedProperties = mutableListOf<StoredPropertyRef>()

        items.forEach { ref ->
            val thir = fileContext.thirOf(ref) ?: return@forEach
            when (ref) {
                is FunctionRef ->
                    fileContext.genFunctionDef(thir as THIR.FunctionDef, ref, writer)

                is GetterRef ->
                    fileContext.genGetterDef(ref, writer)

                is StoredPropertyRef -> {
                    fileContext.genStoredGetter(ref, writer)
                    if (ref.mutable) fileContext.genStoredSetter(ref, writer)
                    storedProperties.add(ref)
                }

                is TypeRef -> classes.add(ref.toClassname() to fileContext.genType(ref))
                is FieldRef, is PackageRef -> Unit
            }
        }

        fileContext.genStaticInitializer(writer, storedProperties, fileContext)
        if (items.any { it !is TypeRef }) classes.add(file.toClassname() + "Bt" to writer.finish())

        return classes
    }

    private fun ParentRef.toClassname(): String = this.toJVMPath(".")

    private fun JC.genType(ref: TypeRef): ByteArray = when (ref) {
        is SingletonTypeRef -> genSingletonType(ref)
        is ProductTypeRef -> genProductType(ref)
    }

    private fun JC.genProductType(ref: ProductTypeRef): ByteArray {
        val `class` = ref.asJVMClass()
        val thir = thirOf(ref) as THIR.ProductTypeDef
        val fields = thir.fields

        val writer = ClassWriter(`class`)

        val ctorDescriptor = thir.ctorDescriptor

        writer.constructor(ctorDescriptor) { ctor ->
            ctor.addVariables(ctorDescriptor.parameters.size)
            ctor.callSuper()

            if (fields.isNotEmpty()) {
                ctor.getThis()
                fields.forEachIndexed { index, field ->
                    if (index != fields.lastIndex)
                        ctor.duplicateLast()

                    ctor.getLocal(index + 1)
                    ctor.setField(
                        `class`,
                        field.jvmName,
                        typeOfField(field)
                    )
                }
            }
            ctor.returnVoid()
        }

        fields.forEach { field ->
            genRecordField(field, writer, `class`)
        }

        return writer.finish()
    }

    context(JC)
    private val THIR.ProductTypeDef.ctorDescriptor get() =
        JVMDescriptor(
            this.fields
                .asSequence()
                .map { typeOfField(it) }
                .toList(), JVMType.Void
        )

    private fun JC.genRecordField(
        field: FieldRef,
        writer: ClassWriter,
        `class`: JVMClass
    ) {
        val type = typeOfField(field)
        val name = field.jvmName

        writer.virtualField(name, type, field.mutable)

        writer.virtualGetter(name, type) { getter ->
            getter.getThis()
            getter.getField(`class`, name, type)
            getter.returnLast()
        }

        if (field.mutable) writer.virtualSetter(name, type) { setter ->
            setter.addVariables(1)
            setter.getThis()
            setter.getLocal(1)
            setter.setField(`class`, name, type)
            setter.returnVoid()
        }
    }

    private fun genSingletonType(ref: SingletonTypeRef): ByteArray {
        val `class` = ref.asJVMClass()
        val type = JVMType.Class(`class`)

        val writer = ClassWriter(`class`)

        writer.constructor(emptyJVMDescriptor, JVMVisibility.PRIVATE) { ctor ->
            ctor.callSuper()
            ctor.returnVoid()
        }

        writer.staticField(instanceName, type, false, JVMVisibility.PUBLIC)

        writer.staticConstructor { staticCtor ->
            staticCtor.instantiate(`class`)
            staticCtor.duplicateLast()
            staticCtor.callConstructor(`class`, emptyJVMDescriptor)
            staticCtor.setStatic(`class`, instanceName, type)
            staticCtor.returnVoid()
        }

        return writer.finish()
    }

    private fun JC.genStaticInitializer(
        writer: ClassWriter,
        constants: List<StoredPropertyRef>,
        fileContext: JC,
    ) = if (constants.isEmpty()) Unit else writer.staticConstructor { staticInitWriter ->
        constants.forEach { ref ->
            val thir = thirOf(ref)?.body!!
            val thirType = thir.type.accessType
            val type = fileContext.jvmTypeOf(thirType)
            val isVoid = thirType.ref is SingletonTypeRef
            if (!isVoid) writer.staticField(ref.jvmName, type, ref.mutable)
            fileContext.genExpr(thir, staticInitWriter, isVoid)
            if (!isVoid) staticInitWriter.setStatic(ref.parent.asJVMClass(), ref.jvmName, type)
        }

        staticInitWriter.returnVoid()
    }

    private fun JC.genFunctionDef(
        def: THIR.FunctionDef,
        ref: FunctionRef,
        writer: ClassWriter,
    ) {
        val sig = signatureOf(ref)
        writer.staticMethod(sig.name, sig.descriptor) { methodWriter ->
            def.params.forEach { methodWriter.addVariable(it.ref) }
            val isVoid = sig.descriptor.returnType == JVMType.Void
            genExpr(def.body, methodWriter, isVoid)
            if (isVoid) methodWriter.returnVoid() else methodWriter.returnLast()
        }
    }

    private fun JC.genGetterDef(
        ref: GetterRef,
        writer: ClassWriter,
    ) {
        val type = jvmTypeOf(typeOf(ref).accessType)
        writer.staticGetter(ref.jvmName, type) { methodWriter ->
            val isVoid = type == JVMType.Void
            genExpr(thirOf(ref)?.body!!, methodWriter, isVoid)
            if (isVoid) methodWriter.returnVoid() else methodWriter.returnLast()
        }
    }

    private fun JC.genStoredGetter(
        ref: StoredPropertyRef,
        writer: ClassWriter,
    ) {
        val thirType = typeOf(ref).accessType
        val isVoid = thirType.ref is SingletonTypeRef
        val type = if (isVoid) JVMType.Void else jvmTypeOf(thirType)
        writer.staticGetter(ref.jvmName, type) { methodVisitor ->
            if (isVoid) {
                methodVisitor.returnVoid()
            } else {
                methodVisitor.getStatic(ref.parent.asJVMClass(), ref.jvmName, type)
                methodVisitor.returnLast()
            }
        }
    }

    private fun JC.genStoredSetter(
        ref: StoredPropertyRef,
        writer: ClassWriter,
    ) {
        val thirType = typeOf(ref).accessType
        val type = jvmTypeOf(thirType)
        writer.staticSetter(ref.jvmName, type) { methodWriter ->
            methodWriter.addVariables(1)

            if (thirType.ref !is SingletonTypeRef) {
                methodWriter.getLocal(0)
                methodWriter.setStatic(ref.parent.asJVMClass(), ref.jvmName, type)
            }

            methodWriter.returnVoid()
        }
    }

    private fun JC.genScopeExpr(
        node: THIR.ScopeExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean,
    ): Boolean {
        val statements = node.statements

        if (statements.isEmpty())
            return false

        statements.subList(0, statements.lastIndex).forEach { genExpr(it, writer, true) }
        return genExpr(statements.last(), writer, ignoreOutput)
    }

    private fun JC.genCallExpr(
        node: THIR.CallExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean,
    ): Boolean {
        node.args.forEach { genExpr(it, writer, false) }
        val signature = signatureOf(node.fn)
        writer.callStatic(signature.parent, signature.name, signature.descriptor)
        val returns = !node.type.isSingleton

        return when {
            returns && ignoreOutput -> {
                writer.popLast()
                false
            }

            !ignoreOutput && !returns ->
                genSingletonAccessExpr(node.type.accessType, writer)

            else -> returns
        }
    }

    private fun JC.typeOfField(ref: FieldRef): JVMType =
        jvmTypeOf(typeOf(ref).accessType)

    private fun JC.genConstructorCallExpr(
        node: THIR.ConstructorCallExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean,
    ): Boolean {
        writer.instantiate(jvmClassOf(node.type))
        if (!ignoreOutput) writer.duplicateLast()
        node.args.forEach { genExpr(it, writer, false) }

        val hir = thirOf(node.type.ref) as THIR.ProductTypeDef
        val ctorDesc = hir.ctorDescriptor

        writer.callConstructor(jvmClassOf(node.type), ctorDesc)
        return ignoreOutput
    }

    private fun genAccessExpr(
        node: THIR.LocalAccessExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean,
    ): Boolean {
        if (ignoreOutput) return false

        writer.getLocal(node.binding)
        return true
    }

    private fun genStringExpr(node: THIR.StringExpr, writer: MethodWriter, ignoreOutput: Boolean): Boolean {
        if (ignoreOutput)
            return false

        writer.loadString(node.rawContext)
        return true
    }

    private fun JC.genSingletonAccessExpr(
        nodeType: PathType,
        writer: MethodWriter,
    ): Boolean {
        val type = jvmClassOf(nodeType)
        writer.getStatic(type, instanceName, JVMType.Class(type))
        return true
    }

    private fun JC.genExpr(
        node: THIR.Expr,
        writer: MethodWriter,
        ignoreOutput: Boolean,
    ): Boolean = when (node) {
        is THIR.StringExpr ->
            genStringExpr(node, writer, ignoreOutput)

        is THIR.CallExpr ->
            genCallExpr(node, writer, ignoreOutput)

        is THIR.ErrorExpr ->
            false

        is THIR.ScopeExpr ->
            genScopeExpr(node, writer, ignoreOutput)

        is THIR.LocalAccessExpr ->
            genAccessExpr(node, writer, ignoreOutput)

        is THIR.SingletonAccessExpr -> {
            if (!ignoreOutput) genSingletonAccessExpr(node.type.accessType, writer)
            ignoreOutput
        }

        is THIR.GetStoredExpr -> genGetStoredExpr(node, writer, ignoreOutput)

        is THIR.SetStoredExpr -> genSetStoredExpr(node, writer)

        is THIR.LocalAssignmentExpr -> genLocalAssignmentExpr(node, writer)

        is THIR.GetFieldExpr -> genGetFieldExpr(node, writer, ignoreOutput)

        is THIR.SetFieldExpr -> genSetFieldExpr(node, writer)

        is THIR.ConstructorCallExpr -> genConstructorCallExpr(node, writer, ignoreOutput)

        is THIR.GetComputedExpr -> genGetComputedExpr(node, writer, ignoreOutput)
    }

    private fun JC.genGetStoredExpr(node: THIR.GetStoredExpr, writer: MethodWriter, ignoreOutput: Boolean): Boolean {
        if (ignoreOutput)
            return true

        val ref = node.property
        writer.callStaticGetter(ref.parent.asJVMClass(), ref.jvmName, jvmTypeOf(node.type.accessType))
        return false
    }

    private fun JC.genGetComputedExpr(
        node: THIR.GetComputedExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean
    ): Boolean {
        if (ignoreOutput)
            return true

        val ref = node.def
        writer.callStaticGetter(ref.parent.asJVMClass(), ref.jvmName, jvmTypeOf(node.type.accessType))
        return false
    }

    private fun JC.genSetStoredExpr(node: THIR.SetStoredExpr, writer: MethodWriter): Boolean {
        genExpr(node.value, writer, false)
        val ref = node.property
        writer.callStaticSetter(ref.parent.asJVMClass(), ref.jvmName, jvmTypeOf(node.value.type.accessType))
        return true
    }

    private fun JC.genLocalAssignmentExpr(node: THIR.LocalAssignmentExpr, writer: MethodWriter): Boolean {
        genExpr(node.value, writer, false)
        writer.setLocal(node.binding)
        return false
    }

    private fun JC.genGetFieldExpr(
        node: THIR.GetFieldExpr,
        writer: MethodWriter,
        ignoreOutput: Boolean
    ): Boolean {
        genExpr(node.on, writer, ignoreOutput)

        if (ignoreOutput) return false

        val getterName = node.field.jvmName
        val descriptor = jvmTypeOf(node.type)
        writer.callVirtualGetter(node.field.parent.asJVMClass(), getterName, descriptor)

        return true
    }

    private fun JC.genSetFieldExpr(
        node: THIR.SetFieldExpr,
        writer: MethodWriter,
    ): Boolean {
        genExpr(node.on, writer, false)
        genExpr(node.value, writer, false)

        val setterName = node.field.jvmName
        val descriptor = jvmTypeOf(node.value.type.accessType)
        writer.callVirtualSetter(node.field.parent.asJVMClass(), setterName, descriptor)

        return false
    }
}