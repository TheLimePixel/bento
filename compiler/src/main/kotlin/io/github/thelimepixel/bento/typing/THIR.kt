package io.github.thelimepixel.bento.typing

import io.github.thelimepixel.bento.binding.ItemRef
import io.github.thelimepixel.bento.binding.LocalRef
import io.github.thelimepixel.bento.binding.MemberRef
import io.github.thelimepixel.bento.parsing.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned
import kotlin.time.measureTime

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
        val local: LocalRef,
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
        val binding: LocalRef,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }

    data class FieldAccessExpr(
        override val ref: ASTRef,
        val field: MemberRef,
        val on: THIR,
    ) : THIR {
        override fun childSequence(): Sequence<THIR> = sequenceOf(on)

        override val type: PathType
            get() = this.field.type?.let(::PathType) ?: BuiltinTypes.nothing
    }

    data class SingletonAccessExpr(
        override val ref: ASTRef,
        override val type: PathType,
    ): THIR {
        override fun childSequence(): Sequence<THIR> = EmptySequence
    }
}