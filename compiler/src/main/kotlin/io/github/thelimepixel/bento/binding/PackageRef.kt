package io.github.thelimepixel.bento.binding

data class PackageRef(val parent: PackageRef?, val name: String) {
    override fun toString(): String {
        val builder = StringBuilder()
        if (parent != null) builder.append(parent.toString()).append("::")
        builder.append(name)
        return builder.toString()
    }
}

private tailrec fun packageRefTo(parent: PackageRef, path: Array<out String>, index: Int): PackageRef =
    if (index == path.size) parent else packageRefTo(PackageRef(parent, path[index]), path, index + 1)


fun packageRefTo(vararg path: String): PackageRef = packageRefTo(PackageRef(null, path[0]), path, 1)