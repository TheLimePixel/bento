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
            importData.packages,
            initialized,
        )
        val (stored, computed) = info.items.partition { it is StoredPropertyRef }
        return stored.associateWith { ref ->
            context.bindLet(ref.ast!!).also { initialized.add(ref as StoredPropertyRef) }
        } + computed.associateWith { ref -> context.bindDefinition(ref) }
    }

    private fun BC.bindDefinition(ref: ItemRef): HIR.Def? {
        val node = ref.ast ?: return null
        return when (val type = node.type) {
            ST.FunDef -> bindFunctionLike(node)
            ST.TypeDef -> bindTypeDef(ref, node)
            else -> error("Unexpected definition type: $type")
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
        ?.firstChild(SyntaxType.Path)
        ?.let {
            val path = bindPath(it, false) ?: return@let null
            val itemRef = path.binding.of
            if (itemRef is TypeRef)
                HIR.TypeRef(it.span, path)
            else null
        }

    private fun LC.findAndBindPattern(node: RedNode, mutable: Boolean): HIR.Pattern? =
        node.firstChild(BaseSets.patterns)?.let {
            when (it.type) {
                ST.IdentPattern -> HIR.IdentPattern(it.span, addLocal(it.rawContent, mutable))
                ST.WildcardPattern -> HIR.WildcardPattern(it.span)
                ST.MutPattern -> HIR.MutablePattern(it.span, findAndBindPattern(it, true))
                else -> error("Unsupported pattern type: ${it.type}")
            }
        }

    private fun LC.bindParamList(node: RedNode): List<HIR.Param>? = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it, false)
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.span, name, type)
        }
        ?.toList()

    private fun BC.bindFunctionLike(node: RedNode): HIR.Def {
        val context = LocalItemBindingContext(this)
        val params = context.bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { context.bindResultingExpression(it) }

        return if (params == null) HIR.GetterDef(node.span, returnType, body)
        else HIR.FunctionDef(node.span, params, returnType, body)
    }

    private fun BC.bindLet(node: RedNode): HIR.LetDef {
        val type = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { bindResultingExpression(it) }
            ?: HIRError.Propagation.at(node.span)
        return HIR.LetDef(node.span, type, body)
    }

    private fun LC.bindCall(node: RedNode): HIR.CallExpr {
        val on = node.firstChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.span)
        val args = node
            .lastChild(ST.ArgList)
            ?.childSequence()
            ?.filter { it.type in BaseSets.expressions }
            ?.map { bindExpr(it) }
            ?.toList()
            ?: emptyList()

        return HIR.CallExpr(node.span, on, args)
    }

    private fun BC.bindIdentifier(node: RedNode, mutable: Boolean): HIR.Expr =
        bindPath(node, mutable) ?: HIR.ErrorExpr(node.span, HIRError.UnboundIdentifier)

    private fun BC.bindPath(node: RedNode, mutable: Boolean): HIR.Path? {
        val segments = node.childSequence().filter { it.type == ST.Identifier }.toList()

        val accessor = if (segments.size == 1) {
            val name = segments[0].rawContent
            if (mutable) accessorFor(name + "_=") else accessorFor(name)
        } else {
            var packNode = packageNodeFor(segments.first().rawContent) ?: return null
            segments.subList(1, segments.lastIndex).forEach {
                packNode = packNode.children[it.rawContent] ?: return null
            }
            val lastName = segments.last().rawContent + if (mutable) "_=" else ""
            astInfoOf(packNode.path)?.accessors?.get(lastName)
        }
        return if (isInitialized(accessor?.of ?: return null)) {
            HIR.Path(node.span, accessor)
        } else null
    }

    private fun LC.bindExpr(node: RedNode, mutable: Boolean = false): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.span, node.content)
        ST.Path -> bindIdentifier(node, mutable)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLetExpr(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        ST.AccessExpr -> bindAccessExpr(node)
        else -> HIR.ErrorExpr(node.span, HIRError.Propagation)
    }

    private fun LC.bindAccessExpr(node: RedNode): HIR.MemberAccessExpr {
        val on = bindExpr(node.firstChild(BaseSets.expressions)!!)
        val field = node.lastChild(ST.Identifier)?.rawContent ?: ""
        return HIR.MemberAccessExpr(node.span, on, field)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = bindExpr(node.firstChild(BaseSets.expressions)!!, true)
        val right = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.span)
        return HIR.AssignmentExpr(node.span, leftRef, right)
    }

    private fun LC.bindParenthesizedExpr(node: RedNode): HIR.Expr =
        node.firstChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.span)

    private fun LC.bindLetExpr(node: RedNode): HIR.LetExpr {
        val pattern = findAndBindPattern(node, false)
        val type = findAndBindTypeAnnotation(node)
        val expr = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.span)

        return HIR.LetExpr(node.span, pattern, type, expr)
    }

    private fun BC.bindResultingExpression(node: RedNode): HIR.Expr =
        LocalItemBindingContext(this).bindExpr(node)

    private fun LC.bindScope(node: RedNode): HIR.ScopeExpr {
        val context = ScopeBindingContext(this)
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { context.bindExpr(it) }
            .toList()

        return HIR.ScopeExpr(node.span, statements)
    }

    override fun bindImport(node: GreenNode?, context: RC): BoundImportData {
        val block = node?.toRedRoot()?.firstChild(ST.ImportBlock) ?: return emptyImportData
        val importedItems = mutableMapOf<String, Accessor>()
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
        importedItems: MutableMap<String, Accessor>,
        importedPackages: MutableMap<String, PackageTreeNode>,
    ): BoundImportPath {
        var lastPackage: PackageTreeNode? = root
        var name = ""
        val segments = node.childSequence()
            .filter { it.type == ST.Identifier }
            .map { segment ->
                name = segment.rawContent
                val lastPack = lastPackage ?: return@map BoundImportPathSegment(segment.span, null, null)
                lastPackage = lastPack.children[name]
                val item = astInfoMap[lastPack.path]?.accessors?.get(name)
                BoundImportPathSegment(segment.span, lastPackage, item)
            }
            .toList()

        segments.lastOrNull()?.let { lastSeg ->
            lastSeg.node?.let { importedPackages[name] = it }
            lastSeg.item?.let { importedItems[name] = it }
        }

        return BoundImportPath(node.span, segments)
    }
}