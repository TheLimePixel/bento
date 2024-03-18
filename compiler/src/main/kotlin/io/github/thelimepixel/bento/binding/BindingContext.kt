package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refFor(name: String): Ref?
    fun packageNodeFor(name: String): PackageTreeNode?
    fun isInitialized(ref: Ref): Boolean
    fun astInfoOf(ref: ParentRef): ASTInfo?
}

class RootBindingContext(
    val parent: BindingContext,
    val root: PackageTreeNode,
    val astInfoMap: InfoMap,
) : BindingContext {
    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)
    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)
    override fun refFor(name: String): Ref? = parent.refFor(name)
    override fun astInfoOf(ref: ParentRef): ASTInfo? = astInfoMap[ref] ?: parent.astInfoOf(ref)
}

class ParentBindingContext(
    private val parent: BindingContext?,
    private val current: ParentRef,
    private val items: Map<String, ItemRef>,
    private val packages: Map<String, PackageTreeNode>,
    private val initialized: Set<ItemRef>,
) : BindingContext {
    override fun refFor(name: String): Ref? =
        items[name] ?: parent?.refFor(name)

    override fun isInitialized(ref: Ref): Boolean =
        ref !is ItemRef || ref.type != ItemType.Constant || ref in initialized || ref.parent != current

    override fun packageNodeFor(name: String): PackageTreeNode? =
        packages[name] ?: parent?.packageNodeFor(name)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent?.astInfoOf(ref)
}

interface LocalBindingContext : BindingContext {
    fun addLocal(name: String, mutable: Boolean): LocalId
    fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, AccessorRef>): LocalId
}

class LocalItemBindingContext(private val parent: BindingContext) : LocalBindingContext {
    private val localsMap = mutableMapOf<String, AccessorRef>()
    private var localCounter = 0

    override fun addLocal(name: String, mutable: Boolean): LocalId =
        addLocalTo(name, mutable, localsMap)

    override fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, AccessorRef>): LocalId {
        val ref = LocalId(localCounter)
        localCounter += 1
        map[name] = AccessorRef(ref, AccessorType.Getter)
        if (mutable) map[name + "_="] = AccessorRef(ref, AccessorType.Setter)
        return ref
    }

    override fun refFor(name: String): Ref? =
        localsMap[name] ?: parent.refFor(name)

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)
}

class ScopeBindingContext(private val parent: LocalBindingContext): LocalBindingContext {
    private val localsMap = mutableMapOf<String, AccessorRef>()

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)

    override fun refFor(name: String): Ref? = localsMap[name] ?: parent.refFor(name)

    override fun addLocal(name: String, mutable: Boolean): LocalId =
        parent.addLocalTo(name, mutable, localsMap)

    override fun addLocalTo(name: String, mutable: Boolean, map: MutableMap<String, AccessorRef>): LocalId =
        parent.addLocalTo(name, mutable, map)
}