package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.ItemPath

data class FunctionSignature(val paramTypes: List<ItemPath>, val returnType: ItemPath)