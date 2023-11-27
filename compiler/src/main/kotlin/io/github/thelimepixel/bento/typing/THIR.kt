package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemPath
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR, THIRError>, Spanned {
    val ref: ASTRef
    val type: ItemPath
    override val span: IntRange
        get() = ref.span

    override val error: THIRError?
        get() = null

    data class ScopeExpr(
        override val ref: ASTRef,
        override val type: ItemPath,
        val statements: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        override val type: ItemPath,
        val fn: ItemRef,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: THIRError,
        override val type: ItemPath,
        val children: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = children.asSequence()
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : THIR {
        override val type: ItemPath
            get() = BuiltinRefs.string

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}