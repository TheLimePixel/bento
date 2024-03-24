package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.utils.Span

data class BoundImportData(
    val accessors: Map<String, Accessor>,
    val packages: Map<String, PackageTreeNode>,
    val binding: List<BoundImportPath>,
)

val emptyImportData = BoundImportData(emptyMap(), emptyMap(), emptyList())

data class BoundImportPath(
    val span: Span,
    val segments: List<BoundImportPathSegment>,
)

data class BoundImportPathSegment(
    val span: Span,
    val node: PackageTreeNode?,
    val item: Accessor?,
)
