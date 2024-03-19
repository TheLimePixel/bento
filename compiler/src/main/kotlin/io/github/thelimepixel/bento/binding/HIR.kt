package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.ASTRef
import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Spanned

sealed interface HIR : CodeTree<HIR, HIRError>, Spanned {
    val ref: ASTRef
    override val span: IntRange
        get() = ref.span
    override val error: HIRError?
        get() = null

    sealed interface Expr : HIR

    data class ScopeExpr(
        override val ref: ASTRef,
        val statements: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = statements.asSequence()
    }

    data class CallExpr(
        override val ref: ASTRef,
        val on: Expr,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(on)
            yieldAll(args)
        }
    }

    data class AssignmentExpr(
        override val ref: ASTRef,
        val left: Expr,
        val right: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            right?.let { yield(it) }
        }
    }

    data class Path(
        override val ref: ASTRef,
        val binding: Accessor,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    sealed interface Pattern : HIR

    data class IdentPattern(override val ref: ASTRef, val local: LocalRef) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class MutablePattern(override val ref: ASTRef, val nested: Pattern?) : Pattern {
        override fun childSequence(): Sequence<HIR> = sequence {
            nested?.let { yield(it) }
        }
    }

    data class WildcardPattern(override val ref: ASTRef) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class LetExpr(
        override val ref: ASTRef,
        val pattern: Pattern?,
        val type: TypeRef?,
        val expr: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            pattern?.let { yield(it) }
            type?.let { yield(it) }
            yield(expr)
        }
    }

    data class ErrorExpr(
        override val ref: ASTRef,
        override val error: HIRError,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val ref: ASTRef,
        val content: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class MemberAccessExpr(
        override val ref: ASTRef,
        val on: Expr,
        val field: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequenceOf(on)
    }

    data class TypeRef(override val ref: ASTRef, val type: Path?) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            type?.let { yield(it) }
        }
    }

    data class Param(override val ref: ASTRef, val pattern: Pattern?, val type: TypeRef?) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            pattern?.let { yield(it) }
            type?.let { yield(it) }
        }
    }

    sealed interface Def : HIR

    sealed interface FunctionLikeDef : Def {
        val params: List<Param>?
        val returnType: TypeRef?
        val body: Expr?

        override fun childSequence(): Sequence<HIR> = sequence {
            params?.let { yieldAll(it) }
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }

    data class FunctionDef(
        override val ref: ASTRef,
        override val params: List<Param>,
        override val returnType: TypeRef?,
        override val body: Expr?
    ) : FunctionLikeDef

    data class GetterDef(
        override val ref: ASTRef,
        override val returnType: TypeRef?,
        override val body: Expr?
    ) : FunctionLikeDef {
        override val params: List<Param>?
            get() = null
    }

    data class LetDef(
        override val ref: ASTRef,
        val type: TypeRef?,
        val expr: Expr,
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            type?.let { yield(it) }
            yield(expr)
        }
    }

    sealed interface TypeDef : Def

    data class SingletonType(override val ref: ASTRef) : TypeDef {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class Field(override val ref: ASTRef, val ident: String, val type: TypeRef?) : Def {
        override fun childSequence(): Sequence<HIR> = sequence { type?.let { yield(it) } }
    }

    data class Constructor(override val ref: ASTRef, val fields: List<ItemRef>) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class RecordType(override val ref: ASTRef, val constructor: Constructor) : TypeDef {
        override fun childSequence(): Sequence<HIR> = sequence { yield(constructor) }
    }
}