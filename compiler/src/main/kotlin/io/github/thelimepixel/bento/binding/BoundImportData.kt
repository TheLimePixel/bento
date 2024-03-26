package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.utils.Span

data class BoundImportData(
    val accessors: Map<String, Accessor>,
    val binding: List<BoundImportPath>,
)

val emptyImportData = BoundImportData(emptyMap(), emptyList())

data class BoundImportPath(
    val span: Span,
    val segments: List<BoundImportPathSegment>,
)

data class BoundImportPathSegment(
    val span: Span,
    val item: Accessor?,
)
