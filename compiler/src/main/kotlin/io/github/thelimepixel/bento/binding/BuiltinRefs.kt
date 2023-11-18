package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = ItemPath(null, "bento")
    val io = bento.subpath("io")
    val println = io.subpath("println")
    val string = bento.subpath("String")
    val unit = bento.subpath("Unit")
    val nothing = bento.subpath("Nothing")
    val map: Map<String, ItemRef> = listOf(
        ItemRef(println, ItemType.Function),
        ItemRef(string, ItemType.Type),
        ItemRef(unit, ItemType.Type),
        ItemRef(nothing, ItemType.Type)
    ).associateBy { it.name }
}