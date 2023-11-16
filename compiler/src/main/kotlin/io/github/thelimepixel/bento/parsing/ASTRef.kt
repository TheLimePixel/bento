package io.github.thelimepixel.bento.parsing

import io.github.thelimepixel.bento.utils.Spanned

data class ASTRef(val type: SyntaxType, override val span: IntRange) : Spanned {
    override fun toString(): String = "$type@$span"
}