package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalId
import io.github.thelimepixel.bento.ast.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface THIR : CodeTree<THIR, THIRError>, Spanned {
    val ref: ASTRef
    val type: Type
    override val span: IntRange
        get() = ref.span

    override val error: THIRError?
        get() = null

    data class ScopeExpr(
        override val ref: ASTRef,
        override val type: Type,
        val statements: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = statements.asSequence()
    }

    data class LetExpr(
        override val ref: ASTRef,
        val local: LocalId?,
        val expr: THIR
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = sequenceOf(expr)
        override val type: Type
            get() = BuiltinTypes.unit
    }

    data class CallExpr(
        override val ref: ASTRef,
        override val type: Type,
        val fn: ItemRef,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ConstructorCallExpr(
        override val ref: ASTRef,
        override val type: PathType,
        val args: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = args.asSequence()
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: THIRError,
        override val type: Type,
        val children: List<THIR>,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = children.asSequence()
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.string

        val rawContext: String
            get() = content.substring(1..<content.lastIndex)

        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAccessExpr(
        override val ref: ASTRef,
        override val type: Type,
        val binding: LocalId,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class LocalAssignmentExpr(
        override val ref: ASTRef,
        val binding: LocalId,
        val value: THIR,
    ) : THIR {
        override val type: Type
            get() = BuiltinTypes.unit
        override fun childSequence(): Sequence<THIR> = sequenceOf(value)
    }

    data class FieldAccessExpr(
        override val ref: ASTRef,
        override val type: PathType,
        val field: ItemRef,
        val on: THIR,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = sequenceOf(on)
    }

    data class SingletonAccessExpr(
        override val ref: ASTRef,
        override val type: PathType,
    ): THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}