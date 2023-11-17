package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = ItemPath(null, "bento")
    val io = bento.subpath("io")
    val println = ItemRef(io, "println", ItemType.Function)
    val string = ItemRef(bento, "String", ItemType.Type)
    val unit = ItemRef(bento, "Unit", ItemType.Type)
    val nothing = ItemRef(bento, "Nothing", ItemType.Type)
    val map: Map<String, ItemRef> = listOf(println, string, unit, nothing).associateBy { it.name }
}