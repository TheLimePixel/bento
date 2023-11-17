package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.BuiltinRefs
import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR, THIRError>, Spanned {
    val ref: ASTRef
    val type: ItemRef
    override val span: IntRange
        get() = ref.span

    override val error: THIRError?
        get() = null

    data class ScopeExpr(
        override val ref: ASTRef,
        override val type: ItemRef,
        val statements: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        override val type: ItemRef,
        val fn: ItemRef,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: THIRError,
    ) : THIR {
        override val type: ItemRef
            get() = BuiltinRefs.nothing

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : THIR {
        override val type: ItemRef
            get() = BuiltinRefs.string

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}