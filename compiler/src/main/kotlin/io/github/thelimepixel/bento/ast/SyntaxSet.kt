package io.github.thelimepixel.bento.ast

import java.util.*

@JvmInline
value class SyntaxSet internal constructor(val set: BitSet) {
    operator fun contains(type: SyntaxType): Boolean = set[type.ordinal]

    fun toArray(): Array<SyntaxType> {
        val res = mutableListOf<SyntaxType>()
        0.rangeTo(set.size()).forEach {
            if (set[it]) res.add(SyntaxType.entries[it])
        }
        return res.toTypedArray()
    }
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
    )
    val paths = syntaxSetOf(
        SyntaxType.Path,
        SyntaxType.NameRef,
    )
    val expressions = syntaxSetOf(
        SyntaxType.StringLiteral,
        SyntaxType.PathExpr,
        SyntaxType.ScopeExpr,
        SyntaxType.CallExpr,
        SyntaxType.ScopeExpr,
        SyntaxType.LetStatement,
        SyntaxType.ParenthesizedExpr,
        SyntaxType.AssignmentExpr,
        SyntaxType.AccessExpr,
        SyntaxType.Error,
    )
    val statements = syntaxSetOf(*expressions.toArray(), SyntaxType.LetStatement)
    val patterns = syntaxSetOf(
        SyntaxType.IdentPattern,
        SyntaxType.WildcardPattern,
        SyntaxType.MutPattern,
        SyntaxType.PathPattern,
        SyntaxType.DestructurePattern,
        SyntaxType.Error,
    )
    val definitions = syntaxSetOf(
        SyntaxType.FunDef,
        SyntaxType.LetDef,
        SyntaxType.TypeDef,
    )
    val typeBodies = syntaxSetOf(
        SyntaxType.Constructor,
    )
    val baseRecoverySet = syntaxSetOf(
        SyntaxType.DefKeyword,
        SyntaxType.LetKeyword,
        SyntaxType.RBrace,
        SyntaxType.EOF,
    )
    val paramListRecoverySet = syntaxSetOf(
        SyntaxType.DefKeyword,
        SyntaxType.EOF,
        SyntaxType.LBrace,
        SyntaxType.RBrace,
    )
    val argListRecoverySet = syntaxSetOf(
        SyntaxType.DefKeyword,
        SyntaxType.EOF,
        SyntaxType.RBrace,
    )
    val scopeRecoverySet = syntaxSetOf(
        SyntaxType.DefKeyword,
        SyntaxType.EOF,
    )
}