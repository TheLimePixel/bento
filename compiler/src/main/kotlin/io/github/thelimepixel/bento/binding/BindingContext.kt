package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.utils.Span

interface BindingContext {
    fun accessorFor(name: String): Accessor?
    fun isInitialized(ref: Ref): Boolean
    fun astInfoOf(ref: ParentRef): ASTInfo?
}

class RootBindingContext(
    val parent: BindingContext,
    private val astInfoMap: InfoMap,
) : BindingContext {
    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)
    override fun accessorFor(name: String): Accessor? = parent.accessorFor(name)
    override fun astInfoOf(ref: ParentRef): ASTInfo? = astInfoMap[ref] ?: parent.astInfoOf(ref)
}

class ParentBindingContext(
    private val parent: BindingContext?,
    private val current: ParentRef,
    private val accessors: Map<String, Accessor>,
    private val initialized: Set<StoredPropertyRef>,
) : BindingContext {
    override fun accessorFor(name: String): Accessor? =
        accessors[name] ?: parent?.accessorFor(name)

    override fun isInitialized(ref: Ref): Boolean =
        ref !is StoredPropertyRef || ref in initialized || ref.parent != current

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent?.astInfoOf(ref)
}

class LocalBindingContext(private val parent: BindingContext) : BindingContext {
    private val localsMap = mutableMapOf<String, Accessor>()

    fun addLocal(name: String, span: Span, mutable: Boolean): LocalRef {
        val ref = LocalRef(span)
        localsMap[name] = Accessor(ref, AccessorType.Get)
        if (mutable) localsMap[name + "_="] = Accessor(ref, AccessorType.Set)
        return ref
    }

    override fun accessorFor(name: String): Accessor? =
        localsMap[name] ?: parent.accessorFor(name)

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)
}