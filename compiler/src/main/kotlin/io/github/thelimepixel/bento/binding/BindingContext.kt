package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refFor(name: String): Ref?
}

class FileBindingContext(
    private val parent: BindingContext?,
    private val map: Map<String, ItemRef>
) : BindingContext {
    override fun refFor(name: String): Ref? = map[name] ?: parent?.refFor(name)
}

class FunctionBindingContext(
    private val parent: BindingContext,
    private val paramMap: Map<String, LocalRef>,
) : BindingContext {
    override fun refFor(name: String): Ref? = paramMap[name] ?: parent.refFor(name)
}

class LocalBindingContext(private val parent: BindingContext) : BindingContext {
    private val localsMap = mutableMapOf<String, LocalRef>()

    fun addLocal(name: String, node: HIR) {
        localsMap[name] = LocalRef(node)
    }
    override fun refFor(name: String): Ref? =
        localsMap[name] ?: parent.refFor(name)
}