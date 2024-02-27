package io.github.thelimepixel.bento.binding

sealed interface PackageTreeNode {
    val path: ItemPath?
    val children: Map<String, PackageTreeNode>
}

private data class MutablePackageTreeNode(
    override val path: ItemPath?
) : PackageTreeNode {
    override val children = mutableMapOf<String, MutablePackageTreeNode>()
}

class PackageTree {
    private val _root = MutablePackageTreeNode(null)
    private val nodes = mutableMapOf<ItemPath?, MutablePackageTreeNode>(null to _root)
    val root: PackageTreeNode get() = _root
    fun get(path: ItemPath?): PackageTreeNode? =
        nodes[path]

    private fun addAndGet(path: ItemPath?): MutablePackageTreeNode {
        if (path == null) return _root
        val node = nodes[path]
        if (node != null) return node
        val parent = addAndGet(path.parent)
        val newNode = MutablePackageTreeNode(path)
        parent.children[path.name] = newNode
        nodes[path] = newNode
        return newNode
    }

    fun add(path: ItemPath): PackageTreeNode = addAndGet(path)
}