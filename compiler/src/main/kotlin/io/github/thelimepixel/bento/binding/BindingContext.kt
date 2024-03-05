package io.github.thelimepixel.bento.binding

interface BindingContext {
    fun refForImmutable(name: String): Ref?
    fun refForMutable(name: String): Ref?
    fun packageNodeFor(name: String): PackageTreeNode?
    fun isInitialized(ref: Ref): Boolean
    fun packageInfoOf(path: PackageRef): PackageASTInfo?
}

class RootBindingContext(
    val parent: BindingContext,
    val root: PackageTreeNode,
    val astInfoMap: PackageInfoMap,
) : BindingContext {
    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)
    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)
    override fun refForImmutable(name: String): Ref? = parent.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = refForMutable(name)
    override fun packageInfoOf(path: PackageRef): PackageASTInfo? = astInfoMap[path] ?: parent.packageInfoOf(path)
}

class PackageBindingContext(
    private val parent: BindingContext?,
    private val currentPackage: SubpackageRef?,
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
        ref !is ItemRef || ref.type != ItemType.Constant || ref in initialized || ref.parent != currentPackage

    override fun packageNodeFor(name: String): PackageTreeNode? =
        packages[name] ?: parent?.packageNodeFor(name)

    override fun packageInfoOf(path: PackageRef): PackageASTInfo? = parent?.packageInfoOf(path)
}

class FunctionBindingContext(
    private val parent: BindingContext,
    private val paramMap: Map<String, LocalRef>,
) : BindingContext {
    override fun refForImmutable(name: String): Ref? = paramMap[name] ?: parent.refForImmutable(name)
    override fun refForMutable(name: String): Ref? = parent.refForMutable(name)
    override fun isInitialized(ref: Ref): Boolean = parent.isInitialized(ref)
    override fun packageNodeFor(name: String): PackageTreeNode? = parent.packageNodeFor(name)
    override fun packageInfoOf(path: PackageRef): PackageASTInfo? = parent.packageInfoOf(path)
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

    override fun packageInfoOf(path: PackageRef): PackageASTInfo? = parent.packageInfoOf(path)
}