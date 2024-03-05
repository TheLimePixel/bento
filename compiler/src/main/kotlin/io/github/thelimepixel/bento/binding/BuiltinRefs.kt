package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = SubpackageRef(RootRef, "bento")
    val io = bento.subpackage("io")
    val println = ItemRef(io,"println", ItemType.Function, 0)
    val string = ItemRef(bento,"String", ItemType.RecordType, 0)
    val unit = ItemRef(bento,"Unit", ItemType.SingletonType, 0)
    val nothing = ItemRef(bento,"Nothing", ItemType.RecordType, 0)
    val map: Map<String, ItemRef> = listOf(
        println,
        string,
        unit,
        nothing,
    ).associateBy { it.name }
}