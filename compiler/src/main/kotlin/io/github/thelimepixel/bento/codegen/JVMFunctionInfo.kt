package io.github.thelimepixel.bento.codegen

import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.typing.BuiltinTypes
import io.github.thelimepixel.bento.typing.THIR
import kotlin.math.max

data class JVMFunctionInfo(val varIds: Map<LocalRef, Int>, val maxStackSize: Int)

fun jvmFunctionInfoOf(hir: HIR.Function, thir: THIR?): JVMFunctionInfo {
    val paramIds = hir.params
        .withIndex()
        .associateByTo(mutableMapOf(), { LocalRef(it.value) }, { it.index })

    return if (thir == null) JVMFunctionInfo(paramIds, 0)
    else JVMInfoResolver(paramIds).apply { handle(thir) }.toInfo()
}

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
        handleRec(node)
        maxStackSize = max(currentStackSize, maxStackSize)
    }

    private fun handleRec(node: THIR) {
        when (node) {
            is THIR.CallExpr -> frame { node.args.forEach { handleRec(it) } }
            is THIR.ScopeExpr -> node.statements.forEach { frame { handleRec(it) } }
            is THIR.AccessExpr, is THIR.ErrorExpr, is THIR.StringExpr -> Unit
        }
        if (node.type != BuiltinTypes.unit && node.type != BuiltinTypes.nothing)
            currentStackSize += 1
    }
}