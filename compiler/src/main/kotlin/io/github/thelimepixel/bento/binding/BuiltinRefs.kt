package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = ItemPath(null, "bento")
    val io = bento.subpath("io")
    val println = ItemRef(io.subpath("println"), ItemType.Function, 0)
    val string = bento.subpath("String")
    val unit = bento.subpath("Unit")
    val nothing = bento.subpath("Nothing")
    val map: Map<String, ItemRef> = listOf(
        println,
        ItemRef(string, ItemType.Type, 0),
        ItemRef(unit, ItemType.Type, 0),
        ItemRef(nothing, ItemType.Type, 0)
    ).associateBy { it.name }
}