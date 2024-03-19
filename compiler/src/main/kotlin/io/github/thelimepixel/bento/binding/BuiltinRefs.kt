package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = SubpackageRef(RootRef, "bento")
    val io = bento.subpackage("io")
    val println = ItemRef(io,"println", 0,ItemType.Function, false)
    val string = ItemRef(bento,"String", 0,ItemType.RecordType, false)
    val unit = ItemRef(bento,"Unit", 0,ItemType.SingletonType, false)
    val nothing = ItemRef(bento,"Nothing", 0,ItemType.RecordType, false)
    val map: Map<String, Accessor> = listOf(
        println,
        string,
        unit,
        nothing,
    ).associateBy({ it.name }) { Accessor(it, AccessorType.Get) }
}