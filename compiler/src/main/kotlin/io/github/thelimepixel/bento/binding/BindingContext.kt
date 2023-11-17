package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refFor(name: String): ItemRef?
}

class ChildBindingContext(
    private val parent: BindingContext?,
    private val map: Map<String, ItemRef>
) : BindingContext {
    override fun refFor(name: String): ItemRef? = map[name] ?: parent?.refFor(name)
}