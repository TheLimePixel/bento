package io.github.thelimepixel.bento.binding

object BuiltinRefs {
    val bento = SubpackageRef(RootRef, "bento")
    val io = bento.subpackage("io")
    val println = FunctionRef(io,"println", 0, null)
    val string = ProductTypeRef(bento,"String", 1, null)
    val unit = SingletonTypeRef(bento,"Unit", 2, null)
    val nothing = ProductTypeRef(bento,"Nothing", 3, null)
    val map: Map<String, Accessor> = listOf(
        println,
        string,
        unit,
        nothing,
    ).associateBy({ it.name }) { Accessor(it, AccessorType.Get) }
}