package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR, THIRError>, Spanned {
    override val span: Span
    val type: Type

    override val error: THIRError?
        get() = null

    data class ScopeExpr(
        override val span: Span,
        override val type: Type,
        val statements: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class LetExpr(
        override val span: Span,
        val local: LocalRef?,
        val expr: THIR
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = sequenceOf(expr)
        override val type: Type
            get() = BuiltinTypes.unit
    }

    data class CallExpr(
        override val span: Span,
        override val type: Type,
        val fn: ItemRef,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class GetComputedExpr(
        override val span: Span,
        override val type: Type,
        val def: ItemRef,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class GetStoredExpr(
        override val span: Span,
        override val type: Type,
        val property: ItemRef,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class SetStoredExpr(
        override val span: Span,
        val property: ItemRef,
        val value: THIR,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.unit
        override fun childSequence(): Sequence<THIR> = sequenceOf(value)
    }

    data class ConstructorCallExpr(
        override val span: Span,
        override val type: PathType,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val span: Span,
        override val error: THIRError,
        override val type: Type,
        val children: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = children.asSequence()
    }

    data class StringExpr(
        override val span: Span,
        val content: String,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.string

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAccessExpr(
        override val span: Span,
        override val type: Type,
        val binding: LocalRef,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAssignmentExpr(
        override val span: Span,
        val binding: LocalRef,
        val value: THIR,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.unit
        override fun childSequence(): Sequence<THIR> = sequenceOf(value)
    }

    data class GetFieldExpr(
        override val span: Span,
        override val type: PathType,
        val field: ItemRef,
        val on: THIR,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = sequenceOf(on)
    }

    data class SetFieldExpr(
        override val span: Span,
        val field: ItemRef,
        val on: THIR,
        val value: THIR,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.unit
        override fun childSequence(): Sequence<THIR> = sequenceOf(on, value)
    }

    data class SingletonAccessExpr(
        override val span: Span,
        override val type: PathType,
    ): THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}