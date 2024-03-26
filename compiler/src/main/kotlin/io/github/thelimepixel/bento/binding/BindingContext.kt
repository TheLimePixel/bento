package io.github.thelimepixel.bento.binding

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

interface LocalBindingContext : BindingContext {
    fun addLocal(name: String, mutable: Boolean): LocalRef
    fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, Accessor>): LocalRef
}

class LocalItemBindingContext(private val parent: BindingContext) : LocalBindingContext {
    private val localsMap = mutableMapOf<String, Accessor>()
    private var localCounter = 0

    override fun addLocal(name: String, mutable: Boolean): LocalRef =
        addLocalTo(name, mutable, localsMap)

    override fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, Accessor>): LocalRef {
        val ref = LocalRef(localCounter)
        localCounter += 1
        map[name] = Accessor(ref, AccessorType.Get)
        if (mutable) map[name + "_="] = Accessor(ref, AccessorType.Set)
        return ref
    }

    override fun accessorFor(name: String): Accessor? =
        localsMap[name] ?: parent.accessorFor(name)

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)
}

class ScopeBindingContext(private val parent: LocalBindingContext) : LocalBindingContext {
    private val localsMap = mutableMapOf<String, Accessor>()

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun accessorFor(name: String): Accessor? = localsMap[name] ?: parent.accessorFor(name)

    override fun addLocal(name: String, mutable: Boolean): LocalRef =
        parent.addLocalTo(name, mutable, localsMap)

    override fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, Accessor>): LocalRef =
        parent.addLocalTo(name, mutable, map)
}