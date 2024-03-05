package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.typing.THIR
import kotlin.math.max

data class JVMFunctionInfo(val varIds: Map<LocalRef, Int>, val maxStackSize: Int)

fun jvmFunctionInfoOf(hir: HIR.FunctionLikeDef, thir: THIR?): JVMFunctionInfo {
    val paramIds = hir.params
        .withIndex()
        .associateByTo(mutableMapOf(), { LocalRef(it.value.pattern) }, { it.index })

    return if (thir == null) JVMFunctionInfo(paramIds, 0)
    else JVMInfoResolver(paramIds).apply { handle(thir) }.toInfo()
}

fun jvmFunctionInfoOf(thir: THIR): JVMFunctionInfo =
    JVMInfoResolver(mutableMapOf()).apply { handle(thir) }.toInfo()

private class JVMInfoResolver(private val varIds: MutableMap<LocalRef, Int>) {
    private var maxStackSize = 0
    private var currentStackSize = 0

    fun toInfo() = JVMFunctionInfo(varIds, maxStackSize)

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
                varIds[node.local] = varIds.size
            }

            is THIR.ErrorExpr -> Unit

            is THIR.LocalAccessExpr, is THIR.StringExpr, is THIR.SingletonAccessExpr -> {
                if (!toIgnore) currentStackSize += 1
            }

            is THIR.FieldAccessExpr -> {
                frame { handleRec(node.on, false) }
                if (!toIgnore) currentStackSize += 1
            }

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