package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.*

private typealias BC = BindingContext
typealias ST = SyntaxType
private typealias LC = LocalBindingContext
private typealias RC = RootBindingContext

interface Binding {
    fun bind(parentRef: ParentRef, importData: BoundImportData, parentContext: BindingContext): Map<ItemRef, HIR.Def?>
    fun bindImport(node: GreenNode?, context: RC): BoundImportData
}

class BentoBinding : Binding {
    override fun bind(
        parentRef: ParentRef,
        importData: BoundImportData,
        parentContext: BindingContext,
    ): Map<ItemRef, HIR.Def?> {
        val initialized = mutableSetOf<StoredPropertyRef>()
        val info = parentContext.astInfoOf(parentRef) ?: return emptyMap()
        val context = ParentBindingContext(
            parentContext,
            parentRef,
            info.accessors + importData.accessors,
            initialized,
        )
        val (stored, computed) = info.items.partition { it is StoredPropertyRef }
        return stored.associateWith { ref ->
            context.bindLet(ref.ast!!.toRoot()).also { initialized.add(ref as StoredPropertyRef) }
        } + computed.associateWith { ref -> context.bindDefinition(ref) }
    }

    private fun BC.bindDefinition(ref: ItemRef): HIR.Def? {
        val node = (ref.ast ?: return null).toRoot()
        return when (val type = node.type) {
            ST.FunDef -> bindFunctionLike(node)
            ST.TypeDef -> bindTypeDef(ref, node)
            else -> error("Unsupported definition type: $type")
        }
    }

    private fun BC.bindTypeDef(ref: ItemRef, node: RedNode): HIR.TypeDef {
        val ctor = node.lastChild(ST.Constructor) ?: return HIR.SingletonType(node.span)
        val refs = astInfoOf(ref as ProductTypeRef)!!.items.asSequence().map { it as FieldRef }
        val fields = ctor.childSequence()
            .filter { it.type == ST.Field }
            .zip(refs) { fieldNode, fieldRef -> bindField(fieldNode, fieldRef) }
            .toList()
        return HIR.ProductType(node.span, fields)
    }

    private fun BC.bindField(node: RedNode, ref: FieldRef): HIR.Field {
        val type = findAndBindTypeAnnotation(node)
        return HIR.Field(node.span, ref, type)
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(BaseSets.paths)
        ?.let { path -> HIR.TypeRef(bindPath(path, false)) }

    private fun LC.findAndBindPattern(parent: RedNode, mutable: Boolean): HIR.Pattern? =
        parent.firstChild(BaseSets.patterns)?.let { node -> bindPattern(node, mutable) }


    private fun LC.bindPattern(node: RedNode, mutable: Boolean) = when (node.type) {
        ST.IdentPattern -> bindIdentPattern(node, mutable)
        ST.PathPattern -> bindPathPattern(node)
        ST.DestructurePattern -> bindDestructurePattern(node, mutable)
        ST.WildcardPattern -> HIR.WildcardPattern(node.span)
        ST.MutPattern -> HIR.MutablePattern(node.span, findAndBindPattern(node, true))
        ST.Error -> HIR.Error(node.span)
        else -> error("Unsupported pattern type: ${node.type}")
    }

    private fun LC.bindIdentPattern(node: RedNode, mutable: Boolean): HIR.Pattern {
        val name = node.rawContent
        val accessor = accessorFor(name)
        return if (accessor?.of is TypeRef)
            HIR.PathPattern(HIR.Identifier(name, accessor, node.span))
        else HIR.IdentPattern(addLocal(node.rawContent, node.span, mutable))
    }

    private fun LC.bindPathPattern(node: RedNode): HIR.Pattern {
        val path = findAndBindPath(node, false)
        return HIR.PathPattern(path)
    }

    private fun LC.bindDestructurePattern(node: RedNode, mutable: Boolean): HIR.Pattern {
        val path = findAndBindPath(node, false)
        val fields = node.childSequence()
            .filter { it.type in BaseSets.patterns }
            .map { bindPattern(it, mutable) }
            .toList()
        return HIR.DestructurePattern(node.span, path, fields)
    }

    private fun LC.bindParamList(node: RedNode): List<HIR.Param>? = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it, false)!!
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.span, name, type)
        }
        ?.toList()

    private fun BC.bindFunctionLike(node: RedNode): HIR.Def {
        val context = LocalBindingContext(this)
        val params = context.bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { context.bindResultingExpression(it) }

        return if (params == null) HIR.GetterDef(node.span, returnType, body)
        else HIR.FunctionDef(node.span, params, returnType, body)
    }

    private fun BC.bindLet(node: RedNode): HIR.LetDef {
        val type = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { bindResultingExpression(it) }
            ?: HIR.Error(node.span)
        return HIR.LetDef(node.span, type, body)
    }

    private fun LC.bindCall(node: RedNode): HIR.CallExpr {
        val on = node.firstChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIR.Error(node.span)
        val args = node
            .lastChild(ST.ArgList)
            ?.childSequence()
            ?.filter { it.type in BaseSets.expressions }
            ?.map { bindExpr(it) }
            ?.toList()
            ?: emptyList()

        return HIR.CallExpr(node.span, on, args)
    }

    private fun BC.findAndBindPath(node: RedNode, mutable: Boolean): HIR.Path =
        bindPath(node.firstChild(BaseSets.paths)!!, mutable)

    private fun BC.bindPath(node: RedNode, mutable: Boolean): HIR.Path = when (node.type) {
        ST.NameRef -> bindNameRef(node, mutable)
        ST.Path -> bindScopeAccess(node, mutable)
        else -> error("Unsupported path type: ${node.type}")
    }

    private fun BC.bindScopeAccess(node: RedNode, mutable: Boolean): HIR.Path {
        val parent = findAndBindPath(node, false)
        return HIR.ScopeAccess(parent, node.span, findAndBindPathSegment(parent, node, mutable))
    }

    private fun BC.findAndBindPathSegment(parent: HIR.Path, node: RedNode, mutable: Boolean): HIR.PathSegment? {
        val segmentNode = node
            .firstChild(ST.PathSegment)
            ?: return null
        val span = segmentNode.span
        val name = segmentNode.rawContent
        val accessedName = name + if (mutable) "_=" else ""
        val parentRef = parent.binding?.of as? ParentRef
            ?: return HIR.PathSegment(name, span, null)
        val accessor = astInfoOf(parentRef)?.accessors?.get(accessedName)
            ?: return HIR.PathSegment(name, span, null)
        return HIR.PathSegment(name, span, accessor)
    }

    private fun BC.bindNameRef(node: RedNode, mutable: Boolean): HIR.Path {
        val name = node.rawContent
        val accessedName = name + if (mutable) "_=" else ""
        val accessor = accessorFor(accessedName)?.let {
            if (isInitialized(it.of)) it
            else null
        }
        return HIR.Identifier(name, accessor, node.span)
    }

    private fun LC.bindExpr(node: RedNode, mutable: Boolean = false): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.span, node.content)
        ST.PathExpr -> findAndBindPath(node, mutable)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        ST.AccessExpr -> bindAccessExpr(node)
        ST.Error -> HIR.Error(node.span)
        else -> error("Unsupported expression type: ${node.type}")
    }

    private fun LC.bindStatement(node: RedNode, mutable: Boolean = false): HIR.Statement = when (node.type) {
        ST.LetStatement -> bindLetExpr(node)
        else -> bindExpr(node, mutable)
    }

    private fun LC.bindAccessExpr(node: RedNode): HIR.MemberAccessExpr {
        val on = bindExpr(node.firstChild(BaseSets.expressions)!!)
        val field = node.lastChild(ST.NameRef)?.rawContent ?: ""
        return HIR.MemberAccessExpr(node.span, on, field)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = bindExpr(node.firstChild(BaseSets.expressions)!!, true)
        val right = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIR.Error(node.span)
        return HIR.AssignmentExpr(node.span, leftRef, right)
    }

    private fun LC.bindParenthesizedExpr(node: RedNode): HIR.Expr =
        node.firstChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIR.Error(node.span)

    private fun LC.bindLetExpr(node: RedNode): HIR.LetStatement {
        val pattern = findAndBindPattern(node, false)
        val type = findAndBindTypeAnnotation(node)
        val expr = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIR.Error(node.span)

        return HIR.LetStatement(node.span, pattern, type, expr)
    }

    private fun BC.bindResultingExpression(node: RedNode): HIR.Expr =
        LocalBindingContext(this).bindExpr(node)

    private fun LC.bindScope(node: RedNode): HIR.ScopeExpr {
        val context = LocalBindingContext(this)
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.statements }
            .map { context.bindStatement(it) }
            .toList()

        return HIR.ScopeExpr(node.span, statements)
    }

    override fun bindImport(node: GreenNode?, context: RC): BoundImportData {
        val block = node?.toRedRoot()?.firstChild(ST.ImportBlock) ?: return emptyImportData
        val importedItems = mutableMapOf<String, Accessor>()
        val paths = block.childSequence()
            .filter { it.type == ST.ImportPath }
            .map {
                context.findAndBindImportPath(it).also { path ->
                    val name = path.lastNameSegment ?: return@also
                    val accessor = path.binding ?: return@also
                    importedItems[name] = accessor
                }
            }
            .toList()

        return BoundImportData(importedItems, paths)
    }

    private fun BC.findAndBindImportPath(node: RedNode): HIR.Path {
        val pathNode = node.firstChild(BaseSets.paths)!!
        return when (pathNode.type) {
            ST.NameRef -> bindImportNameRef(pathNode)
            ST.Path -> bindImportScopeAccess(pathNode)
            else -> error("Got invalid path type: ${pathNode.type}")
        }
    }

    private fun BC.bindImportScopeAccess(node: RedNode): HIR.Path {
        val parent = findAndBindImportPath(node)
        return HIR.ScopeAccess(parent, node.span, findAndBindPathSegment(parent, node, false))
    }

    private fun BC.bindImportNameRef(node: RedNode): HIR.Path {
        val name = node.rawContent
        val accessor = astInfoOf(RootRef)!!.accessors[name]
        return HIR.Identifier(name, accessor, node.span)
    }
}