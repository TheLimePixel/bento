package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs

object BuiltinTypes {
    val string = PathType(BuiltinRefs.string)
    val unit = PathType(BuiltinRefs.unit)
    val nothing = PathType(BuiltinRefs.nothing)
}