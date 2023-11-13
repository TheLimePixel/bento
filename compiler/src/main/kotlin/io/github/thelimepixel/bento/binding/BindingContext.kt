package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refFor(name: String): FunctionRef?
}

class ChildBindingContext(
    private val parent: BindingContext,
    private val map: Map<String, FunctionRef>
) : BindingContext {
    override fun refFor(name: String): FunctionRef? = map[name] ?: parent.refFor(name)
}

class TopLevelBindingContext : BindingContext {
    override fun refFor(name: String): FunctionRef? = when (name) {
        "println" -> FunctionRef.Special.println
        else -> null
    }
}