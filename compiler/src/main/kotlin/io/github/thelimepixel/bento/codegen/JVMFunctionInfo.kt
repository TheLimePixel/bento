package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.binding.findId
import io.github.thelimepixel.bento.typing.THIR
import kotlin.math.max

data class JVMFunctionInfo(val maxLocals: Int, val maxStackSize: Int, val varIds: Map<LocalRef, Int>)

fun jvmFunctionInfoOf(hir: HIR.FunctionLikeDef, thir: THIR?): JVMFunctionInfo {
    val resolver = JVMInfoResolver()
    hir.params?.forEach {
        it.pattern?.findId()?.let { id -> resolver.addLocal(id) } ?: resolver.addEmptyParam()
    }

    if (thir != null) resolver.handle(thir)

    return resolver.toInfo()
}

fun jvmFunctionInfoOf(thir: THIR): JVMFunctionInfo =
    JVMInfoResolver().apply { handle(thir) }.toInfo()

private class JVMInfoResolver {
    private var maxStackSize = 0
    private var currentStackSize = 0
    private var maxLocals = 0
    private val localMapping = mutableMapOf<LocalRef, Int>()

    fun toInfo() = JVMFunctionInfo(maxLocals, maxStackSize, localMapping)

    fun addLocal(id: LocalRef) {
        localMapping[id] = maxLocals
        maxLocals++
    }

    fun addEmptyParam() {
        maxLocals++
    }

    private inline fun frame(fn: () -> Unit) {
        val lastSize = currentStackSize
        fn()
        maxStackSize = max(currentStackSize, maxStackSize)
        currentStackSize = lastSize
    }

    fun handle(node: THIR) {
        handleRec(node, node.type.isSingleton)
        maxStackSize = max(currentStackSize, maxStackSize)
    }

    private fun handleRec(node: THIR, toIgnore: Boolean) {
        when (node) {
            is THIR.CallExpr -> {
                frame { node.args.forEach { handleRec(it, false) } }
                if (!toIgnore || !node.type.isSingleton) currentStackSize += 1
            }

            is THIR.ScopeExpr -> {
                val statements = node.statements
                if (statements.isEmpty()) return
                statements
                    .subList(0, node.statements.lastIndex)
                    .forEach { frame { handleRec(it, true) } }
                return frame { handleRec(statements.last(), toIgnore) }
            }

            is THIR.LetExpr -> {
                frame { handleRec(node.expr, false) }
                addLocal(node.local ?: return)
            }

            is THIR.ErrorExpr -> Unit

            is THIR.LocalAccessExpr, is THIR.StringExpr, is THIR.SingletonAccessExpr -> {
                if (!toIgnore) currentStackSize += 1
            }

            is THIR.LocalAssignmentExpr -> {
                frame { handleRec(node.value, false) }
            }

            is THIR.GetFieldExpr -> {
                frame { handleRec(node.on, false) }
                if (!toIgnore) currentStackSize += 1
            }

            is THIR.SetFieldExpr -> {
                frame {
                    handleRec(node.on, false)
                    handleRec(node.value, false)
                }
            }

            is THIR.GetStoredExpr ->
                currentStackSize += 1

            is THIR.SetStoredExpr ->
                frame { handleRec(node.value, false) }

            is THIR.ConstructorCallExpr -> {
                if (!toIgnore) currentStackSize += 1
                frame {
                    currentStackSize += 1
                    node.args.forEach { handleRec(it, false) }
                }
            }
        }
    }
}