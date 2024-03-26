package io.github.thelimepixel.bento.binding

data class BoundImportData(
    val accessors: Map<String, Accessor>,
    val paths: List<HIR.Path>,
)

val emptyImportData = BoundImportData(emptyMap(), emptyList())