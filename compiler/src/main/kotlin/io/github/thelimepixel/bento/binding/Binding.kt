package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.ast.*

private typealias BC = BindingContext
typealias ST = SyntaxType
private typealias LC = LocalBindingContext
private typealias RC = RootBindingContext

interface Binding {
    fun bind(parentRef: ParentRef, importData: BoundImportData, parentContext: BindingContext): Map<ItemRef, HIR.Def>
    fun bindImport(node: GreenNode?, context: RC): BoundImportData
}

class BentoBinding : Binding {
    override fun bind(
        parentRef: ParentRef,
        importData: BoundImportData,
        parentContext: BindingContext,
    ): Map<ItemRef, HIR.Def> {
        val initialized = mutableSetOf<ItemRef>()
        val info = parentContext.astInfoOf(parentRef) ?: return emptyMap()
        val context = ParentBindingContext(
            parentContext,
            parentRef,
            info.items.associateBy { it.name } + importData.items,
            importData.packages,
            initialized,
        )
        return info.items.associateWith { ref ->
            context.bindDefinition(ref, info.dataMap[ref.name]!![ref.index].toRedRoot())
                .also { initialized.add(ref) }
        }
    }

    private fun BC.bindDefinition(ref: ParentRef, node: RedNode): HIR.Def = when (node.type) {
        ST.FunDef -> bindFunctionLike(node)
        ST.LetDef -> bindLet(node)
        ST.TypeDef -> bindTypeDef(ref, node)
        ST.Field -> bindField(node)
        else -> error("Unsupported definition type")
    }

    private fun BC.bindTypeDef(ref: ParentRef, node: RedNode): HIR.TypeDef {
        val ctor = node.lastChild(ST.Constructor) ?: return HIR.SingletonType(node.ref)
        val fields = astInfoOf(ref)!!.items
        return HIR.RecordType(node.ref, HIR.Constructor(ctor.ref, fields))
    }

    private fun BC.bindField(node: RedNode): HIR.Field {
        val name = node.firstChild(ST.Identifier)?.rawContent ?: ""
        val type = findAndBindTypeAnnotation(node)
        return HIR.Field(node.ref, name, type)
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(SyntaxType.Path)
        ?.let {
            val itemRef = handlePath(it, false)
            if (itemRef is ItemRef && itemRef.type.isType)
                HIR.TypeRef(it.ref, itemRef)
            else null
        }

    private fun findAndBindPattern(node: RedNode): HIR.Pattern = node.firstChild(BaseSets.patterns)?.let {
        when (it.type) {
            ST.IdentPattern -> HIR.IdentPattern(it.ref, it.rawContent)
            ST.WildcardPattern -> HIR.WildcardPattern(it.ref)
            else -> error("Unsupported pattern type: ${it.type}")
        }
    } ?: HIRError.Propagation.at(node.ref)

    private fun BC.bindParamList(node: RedNode): List<HIR.Param>? = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it)
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.ref, name, type)
        }
        ?.toList()

    private fun BC.bindFunctionLike(node: RedNode): HIR.FunctionLikeDef {
        val params = bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val context = if (params == null) this else {
            val paramsMap = params.asSequence()
                .mapNotNull { it.pattern as? HIR.IdentPattern }
                .associateBy({ it.name }) { LocalRef((it)) }

            FunctionBindingContext(this, paramsMap)
        }
        val body = node.lastChild(BaseSets.expressions)?.let { context.bindResultingExpression(it) }

        return if (params == null) HIR.GetterDef(node.ref, returnType, body)
        else HIR.FunctionDef(node.ref, params, returnType, body)
    }

    private fun BC.bindLet(node: RedNode): HIR.ConstantDef {
        val type = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { bindResultingExpression(it) }
            ?: HIRError.Propagation.at(node.ref)
        return HIR.ConstantDef(node.ref, type, body)
    }

    private fun LC.bindCall(node: RedNode): HIR.CallExpr {
        val on = node.firstChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.ref)
        val args = node
            .lastChild(ST.ArgList)
            ?.childSequence()
            ?.filter { it.type in BaseSets.expressions }
            ?.map { bindExpr(it) }
            ?.toList()
            ?: emptyList()

        return HIR.CallExpr(node.ref, on, args)
    }

    private fun BC.bindIdentifier(node: RedNode) = handlePath(node, mutable = false)
        ?.let {
            if (isInitialized(it)) HIR.PathExpr(node.ref, it)
            else HIR.ErrorExpr(node.ref, HIRError.UninitializedConstant)
        } ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

    private fun BC.handlePath(node: RedNode, mutable: Boolean): Ref? {
        val segments = node.childSequence().filter { it.type == ST.Identifier }.toList()

        if (segments.size == 1) {
            val name = segments[0].rawContent
            return if (mutable) refFor(name + "_=") else refFor(name)
        }

        var packNode = packageNodeFor(segments.first().rawContent) ?: return null
        segments.subList(1, segments.lastIndex).forEach {
            packNode = packNode.children[it.rawContent] ?: return null
        }
        val lastName = segments.last().rawContent + if (mutable) "_=" else ""
        return (astInfoOf(packNode.path) ?: return null)
            .items.lastOrNull { it.name == lastName }
    }

    private fun LC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)
        ST.Path -> bindIdentifier(node)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLet(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        ST.AccessExpr -> bindAccessExpr(node)
        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun LC.bindAccessExpr(node: RedNode): HIR.AccessExpr {
        val on = bindExpr(node.firstChild(BaseSets.expressions)!!)
        val field = node.lastChild(ST.Identifier)?.rawContent ?: ""
        return HIR.AccessExpr(node.ref, on, field)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = node.firstChild(BaseSets.expressions)?.let { expr ->
            if (expr.type == ST.Path) handlePath(expr, mutable = true)
            else null
        }
        val right = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.ref)
        return HIR.AssignmentExpr(node.ref, leftRef, right)
    }

    private fun LC.bindParenthesizedExpr(node: RedNode): HIR.Expr =
        node.firstChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

    private fun LC.bindLet(node: RedNode): HIR.LetExpr {
        val pattern = findAndBindPattern(node)
        val type = findAndBindTypeAnnotation(node)
        val expr = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

        if (pattern is HIR.IdentPattern)
            addLocal(pattern.name, pattern)

        return HIR.LetExpr(node.ref, pattern, type, expr)
    }

    private fun BC.bindResultingExpression(node: RedNode): HIR.Expr =
        LocalBindingContext(this).bindExpr(node)

    private fun BC.bindScope(node: RedNode): HIR.ScopeExpr {
        val context = LocalBindingContext(this)
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { context.bindExpr(it) }
            .toList()

        return HIR.ScopeExpr(node.ref, statements)
    }

    override fun bindImport(node: GreenNode?, context: RC): BoundImportData {
        val block = node?.toRedRoot()?.firstChild(ST.ImportBlock) ?: return emptyImportData
        val importedItems = mutableMapOf<String, ItemRef>()
        val importedPackages = mutableMapOf<String, PackageTreeNode>()
        val paths = block.childSequence()
            .filter { it.type == ST.ImportPath }
            .map {
                context.bindImportPath(it, importedItems, importedPackages)
            }
            .toList()

        return BoundImportData(importedItems, importedPackages, paths)
    }

    private fun RC.bindImportPath(
        node: RedNode,
        importedItems: MutableMap<String, ItemRef>,
        importedPackages: MutableMap<String, PackageTreeNode>,
    ): BoundImportPath {
        var lastPackage: PackageTreeNode? = root
        var name = ""
        val segments = node.childSequence()
            .filter { it.type == ST.Identifier }
            .map { segment ->
                name = segment.rawContent
                val lastPack = lastPackage ?: return@map BoundImportPathSegment(segment.ref, null, emptyList())
                lastPackage = lastPack.children[name]
                val items =
                    astInfoMap[lastPack.path]?.items?.filter { it.name == name } ?: emptyList()
                BoundImportPathSegment(segment.ref, lastPackage, items)
            }
            .toList()

        segments.lastOrNull()?.let { lastSeg ->
            lastSeg.node?.let { importedPackages[name] = it }
            lastSeg.items.forEach {
                importedItems[name] = it
            }
        }

        return BoundImportPath(node.ref, segments)
    }
}