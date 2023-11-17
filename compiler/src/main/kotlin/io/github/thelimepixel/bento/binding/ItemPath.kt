package io.github.thelimepixel.bento.binding

data class ItemPath(val parent: ItemPath?, val name: String) {
    override fun toString(): String {
        val builder = StringBuilder()
        if (parent != null) builder.append(parent.toString()).append("::")
        builder.append(name)
        return builder.toString()
    }

    fun subpath(name: String) = ItemPath(this, name)
}

private tailrec fun pathOf(parent: ItemPath, path: Array<out String>, index: Int): ItemPath =
    if (index == path.size) parent else pathOf(ItemPath(parent, path[index]), path, index + 1)


fun pathOf(vararg path: String): ItemPath = pathOf(ItemPath(null, path[0]), path, 1)