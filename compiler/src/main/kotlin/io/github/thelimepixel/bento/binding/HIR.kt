package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.utils.CodeTree
import io.github.thelimepixel.bento.utils.EmptySequence
import io.github.thelimepixel.bento.utils.Span
import io.github.thelimepixel.bento.utils.Spanned

sealed interface HIR : Spanned, CodeTree<HIR> {
    override val span: Span
    override fun childSequence(): Sequence<HIR>

    sealed interface Statement : HIR
    sealed interface Expr : Statement


    data class ScopeExpr(
        override val span: Span,
        val statements: List<Statement>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = statements.asSequence()
    }

    data class CallExpr(
        override val span: Span,
        val on: Expr,
        val args: List<Expr>,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(on)
            yieldAll(args)
        }
    }

    data class AssignmentExpr(
        override val span: Span,
        val left: Expr,
        val right: Expr,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(left)
            yield(right)
        }
    }

    sealed interface Path : Expr {
        val binding: Accessor?
        val lastNameSegment: String?
    }

    data class ScopeAccess(
        val prefix: Path,
        override val span: Span,
        val segment: PathSegment?,
    ) : Path {
        override val binding: Accessor?
            get() = segment?.binding

        override fun childSequence(): Sequence<HIR> = EmptySequence
        override val lastNameSegment: String?
            get() = segment?.name
    }

    data class PathSegment(
        val name: String,
        override val span: Span,
        val binding: Accessor?
    ) : HIR {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class Identifier(
        override val lastNameSegment: String,
        override val binding: Accessor?,
        override val span: Span
    ) : Path {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    sealed interface Pattern : HIR

    data class IdentPattern(val local: LocalRef) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
        override val span: Span
            get() = local.span
    }

    data class MutablePattern(override val span: Span, val nested: Pattern?) : Pattern {
        override fun childSequence(): Sequence<HIR> = sequence {
            nested?.let { yield(it) }
        }
    }

    data class WildcardPattern(override val span: Span) : Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class PathPattern(val path: Path) : Pattern {
        override fun childSequence(): Sequence<HIR> = sequenceOf(path)
        override val span: Span
            get() = path.span
    }

    data class DestructurePattern(
        override val span: Span,
        val path: Path,
        val fields: List<Pattern>,
    ) : Pattern {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(path)
            yieldAll(fields)
        }
    }

    data class LetStatement(
        override val span: Span,
        val pattern: Pattern?,
        val type: TypeRef?,
        val expr: Expr,
    ) : Statement {
        override fun childSequence(): Sequence<HIR> = sequence {
            pattern?.let { yield(it) }
            type?.let { yield(it) }
            yield(expr)
        }
    }

    data class Error(
        override val span: Span,
    ) : Expr, Pattern {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class StringExpr(
        override val span: Span,
        val content: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class MemberAccessExpr(
        override val span: Span,
        val on: Expr,
        val field: String,
    ) : Expr {
        override fun childSequence(): Sequence<HIR> = sequenceOf(on)
    }

    data class TypeRef(val type: Path) : HIR {
        override fun childSequence(): Sequence<HIR> = sequenceOf(type)
        override val span: Span
            get() = type.span
    }

    data class Param(override val span: Span, val pattern: Pattern, val type: TypeRef?) : HIR {
        override fun childSequence(): Sequence<HIR> = sequence {
            yield(pattern)
            type?.let { yield(it) }
        }
    }

    sealed interface Def : HIR

    data class FunctionDef(
        override val span: Span,
        val params: List<Param>,
        val returnType: TypeRef?,
        val body: Expr?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            yieldAll(params)
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }

    data class GetterDef(
        override val span: Span,
        val returnType: TypeRef?,
        val body: Expr?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            returnType?.let { yield(it) }
            body?.let { yield(it) }
        }
    }

    data class LetDef(
        override val span: Span,
        val type: TypeRef?,
        val expr: Expr,
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence {
            type?.let { yield(it) }
            yield(expr)
        }
    }

    sealed interface TypeDef : Def

    data class SingletonType(override val span: Span) : TypeDef {
        override fun childSequence(): Sequence<HIR> = EmptySequence
    }

    data class Field(
        override val span: Span,
        val ref: FieldRef,
        val type: TypeRef?
    ) : Def {
        override fun childSequence(): Sequence<HIR> = sequence { type?.let { yield(it) } }
    }

    data class ProductType(override val span: Span, val fields: List<Field>) : TypeDef {
        override fun childSequence(): Sequence<HIR> = fields.asSequence()
    }
}

typealias HIRMap = Map<ItemRef, HIR.Def?>