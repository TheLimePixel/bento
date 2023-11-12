package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refFor(name: String): FunctionRef?
}

class ChildContext(private val parent: BindingContext, private val map: Map<String, FunctionRef>) : BindingContext {
    override fun refFor(name: String): FunctionRef? = map[name] ?: parent.refFor(name)
}

object TopLevelContext : BindingContext {
    override fun refFor(name: String): FunctionRef? = when (name) {
        "println" -> FunctionRef.Special.println
        else -> null
    }
}