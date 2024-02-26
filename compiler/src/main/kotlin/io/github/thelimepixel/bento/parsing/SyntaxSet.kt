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
    val identifiers = syntaxSetOf(
        SyntaxType.StandardIdentifier,
        SyntaxType.BacktickedIdentifier,
        SyntaxType.GetKeyword,
        SyntaxType.SetKeyword,
    )
    val expressions = syntaxSetOf(
        SyntaxType.StringLiteral,
        SyntaxType.Identifier,
        SyntaxType.ScopeExpr,
        SyntaxType.CallExpr,
        SyntaxType.ScopeExpr,
        SyntaxType.LetExpr,
        SyntaxType.ParenthesizedExpr,
        SyntaxType.AssignmentExpr,
    )
    val patterns = syntaxSetOf(
        SyntaxType.Identifier,
        SyntaxType.Wildcard,
    )
    val definitions = syntaxSetOf(
        SyntaxType.FunDef,
        SyntaxType.GetDef,
        SyntaxType.SetDef,
    )
}