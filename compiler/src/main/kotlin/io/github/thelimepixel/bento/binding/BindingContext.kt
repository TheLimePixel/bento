package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refForImmutable(name: String): Ref?
    fun refForMutable(name: String): Ref?
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
    override fun refForImmutable(name: String): Ref? = parent.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = refForMutable(name)
    override fun astInfoOf(ref: ParentRef): ASTInfo? = astInfoMap[ref] ?: parent.astInfoOf(ref)
}

class ParentBindingContext(
    private val parent: BindingContext?,
    private val current: ParentRef,
    private val immutables: Map<String, ItemRef>,
    private val mutables: Map<String, ItemRef>,
    private val packages: Map<String, PackageTreeNode>,
    private val initialized: Set<ItemRef>,
) : BindingContext {
    override fun refForImmutable(name: String): Ref? =
        immutables[name] ?: parent?.refForImmutable(name)

    override fun refForMutable(name: String): Ref? =
        mutables[name] ?: parent?.refForMutable(name)

    override fun isInitialized(ref: Ref): Boolean =
        ref !is ItemRef || ref.type != ItemType.Constant || ref in initialized || ref.parent != current

    override fun packageNodeFor(name: String): PackageTreeNode? =
        packages[name] ?: parent?.packageNodeFor(name)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent?.astInfoOf(ref)
}

class FunctionBindingContext(
    private val parent: BindingContext,
    private val paramMap: Map<String, LocalRef>,
) : BindingContext {
    override fun refForImmutable(name: String): Ref? = paramMap[name] ?: parent.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = parent.refForMutable(name)
    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)
    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)
    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)
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

    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)

    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)

    override fun astInfoOf(ref: ParentRef): ASTInfo? = parent.astInfoOf(ref)
}