package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.HIR
import io.github.thelimepixel.bento.binding.ItemPath

data class FunctionSignature(val paramTypes: List<ItemPath>, val returnType: ItemPath)

fun HIR.Function.signature(): FunctionSignature =
    FunctionSignature(emptyList(), this.returnType?.type ?: BuiltinRefs.unit)