package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refForImmutable(name: String): Ref?
    fun refForMutable(name: String): Ref?
}

class FileBindingContext(
    private val parent: BindingContext?,
    private val immutables: Map<String, ItemRef>,
    private val mutables: Map<String, ItemRef>,
) : BindingContext {
    override fun refForImmutable(name: String): Ref? = immutables[name] ?: parent?.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = mutables[name] ?: parent?.refForMutable(name)
}

class FunctionBindingContext(
    private val parent: BindingContext,
    private val paramMap: Map<String, LocalRef>,
) : BindingContext {
    override fun refForImmutable(name: String): Ref? = paramMap[name] ?: parent.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = parent.refForMutable(name)
}

class LocalBindingContext(private val parent: BindingContext) : BindingContext {
    private val localsMap = mutableMapOf<String, LocalRef>()

    fun addLocal(name: String, node: HIR.Pattern) {
        localsMap[name] = LocalRef(node)
    }
    override fun refForImmutable(name: String): Ref? =
        localsMap[name] ?: parent.refForImmutable(name)

    override fun refForMutable(name: String): Ref? =
        parent.refForMutable(name)
}