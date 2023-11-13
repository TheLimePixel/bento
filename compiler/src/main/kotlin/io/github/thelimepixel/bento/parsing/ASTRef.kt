package io.github.thelimepixel.bento.parsing

data class ASTRef(val span: IntRange, val type: SyntaxType) {
    override fun toString(): String = "$type@$span"
}