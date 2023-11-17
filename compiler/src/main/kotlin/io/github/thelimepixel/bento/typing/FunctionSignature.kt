package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.ItemRef

data class FunctionSignature(val paramTypes: List<ItemRef>, val returnType: ItemRef)