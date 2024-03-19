package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.ASTRef

data class BoundImportData(
    val accessors: Map<String, Accessor>,
    val packages: Map<String, PackageTreeNode>,
    val binding: List<BoundImportPath>,
)

val emptyImportData = BoundImportData(emptyMap(), emptyMap(), emptyList())

data class BoundImportPath(
    val ref: ASTRef,
    val segments: List<BoundImportPathSegment>,
)

data class BoundImportPathSegment(
    val ref: ASTRef,
    val node: PackageTreeNode?,
    val item: Accessor?,
)
