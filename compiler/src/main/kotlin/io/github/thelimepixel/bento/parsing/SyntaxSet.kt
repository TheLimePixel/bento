package io.github.thelimepixel.bento.parsing

import java.util.*

@JvmInline
value class SyntaxSet internal constructor(val set: BitSet) {
    operator fun contains(type: SyntaxType): Boolean = set[type.ordinal]
}

fun syntaxSetOf(vararg types: SyntaxType): SyntaxSet {
    val set = BitSet(SyntaxType.entries.size)
    types.forEach { set[it.ordinal] = true }
    return SyntaxSet(set)
}

object BaseSets {
    val expressions = syntaxSetOf(
        SyntaxType.StringLiteral,
        SyntaxType.Identifier,
        SyntaxType.ScopeExpr,
        SyntaxType.CallExpr
    )
}