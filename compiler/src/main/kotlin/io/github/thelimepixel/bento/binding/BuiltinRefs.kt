package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = ItemPath(null, "bento")
    val io = bento.subpath("io")
    val println = ItemRef(io.subpath("println"), ItemType.Function, 0)
    val string = ItemRef(bento.subpath("String"), ItemType.RecordType, 0)
    val unit = ItemRef(bento.subpath("Unit"), ItemType.SingletonType, 0)
    val nothing = ItemRef(bento.subpath("Nothing"), ItemType.RecordType, 0)
    val map: Map<String, ItemRef> = listOf(
        println,
        string,
        unit,
        nothing,
    ).associateBy { it.name }
}