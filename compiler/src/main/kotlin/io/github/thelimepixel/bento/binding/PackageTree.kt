package io.github.thelimepixel.bento.binding

sealed interface PackageTreeNode {
    val path: PackageRef
    val children: Map<String, PackageTreeNode>
}

private data class MutablePackageTreeNode(
    override val path: PackageRef
) : PackageTreeNode {
    override val children = mutableMapOf<String, MutablePackageTreeNode>()

    override fun toString(): String = "$path: $children"
}

class PackageTree {
    private val _root = MutablePackageTreeNode(RootRef)
    private val nodes = mutableMapOf<PackageRef, MutablePackageTreeNode>(RootRef to _root)
    val root: PackageTreeNode get() = _root
    fun get(path: PackageRef): PackageTreeNode? =
        nodes[path]

    private fun addAndGet(path: PackageRef): MutablePackageTreeNode {
        if (path !is SubpackageRef) return _root
        val node = nodes[path]
        if (node != null) return node
        val parent = addAndGet(path.parent)
        val newNode = MutablePackageTreeNode(path)
        parent.children[path.name] = newNode
        nodes[path] = newNode
        return newNode
    }

    fun add(path: SubpackageRef): PackageTreeNode = addAndGet(path)
}