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
            info.accessors + importData.accessors,
            importData.packages,
            initialized,
        )
        val (stored, computed) = info.items.partition { it.type == ItemType.StoredProperty }
        return stored.associateWith { ref ->
            context.bindDefinition(ref, info.dataMap[ref.name]!![ref.index].toRedRoot())
                .also { initialized.add(ref) }
        } + computed.associateWith { ref ->
            context.bindDefinition(ref, info.dataMap[ref.name]!![ref.index].toRedRoot())
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
            val path = bindPath(it, false) ?: return@let null
            val itemRef = path.binding.of
            if (itemRef is ItemRef && itemRef.type.isType)
                HIR.TypeRef(it.ref, path)
            else null
        }

    private fun LC.findAndBindPattern(node: RedNode, mutable: Boolean): HIR.Pattern? =
        node.firstChild(BaseSets.patterns)?.let {
            when (it.type) {
                ST.IdentPattern -> HIR.IdentPattern(it.ref, addLocal(it.rawContent, mutable))
                ST.WildcardPattern -> HIR.WildcardPattern(it.ref)
                ST.MutPattern -> HIR.MutablePattern(it.ref, findAndBindPattern(it, true))
                else -> error("Unsupported pattern type: ${it.type}")
            }
        }

    private fun LC.bindParamList(node: RedNode): List<HIR.Param>? = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it, false)
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.ref, name, type)
        }
        ?.toList()

    private fun BC.bindFunctionLike(node: RedNode): HIR.FunctionLikeDef {
        val context = LocalItemBindingContext(this)
        val params = context.bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { context.bindResultingExpression(it) }

        return if (params == null) HIR.GetterDef(node.ref, returnType, body)
        else HIR.FunctionDef(node.ref, params, returnType, body)
    }

    private fun BC.bindLet(node: RedNode): HIR.LetDef {
        val type = findAndBindTypeAnnotation(node)
        val body = node.lastChild(BaseSets.expressions)?.let { bindResultingExpression(it) }
            ?: HIRError.Propagation.at(node.ref)
        return HIR.LetDef(node.ref, type, body)
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

    private fun BC.bindIdentifier(node: RedNode, mutable: Boolean): HIR.Expr =
        bindPath(node, mutable) ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

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
            HIR.Path(node.ref, accessor)
        } else null
    }

    private fun LC.bindExpr(node: RedNode, mutable: Boolean = false): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)
        ST.Path -> bindIdentifier(node, mutable)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLetExpr(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        ST.AccessExpr -> bindAccessExpr(node)
        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun LC.bindAccessExpr(node: RedNode): HIR.MemberAccessExpr {
        val on = bindExpr(node.firstChild(BaseSets.expressions)!!)
        val field = node.lastChild(ST.Identifier)?.rawContent ?: ""
        return HIR.MemberAccessExpr(node.ref, on, field)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = bindExpr(node.firstChild(BaseSets.expressions)!!, true)
        val right = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) } ?: HIRError.Propagation.at(node.ref)
        return HIR.AssignmentExpr(node.ref, leftRef, right)
    }

    private fun LC.bindParenthesizedExpr(node: RedNode): HIR.Expr =
        node.firstChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

    private fun LC.bindLetExpr(node: RedNode): HIR.LetExpr {
        val pattern = findAndBindPattern(node, false)
        val type = findAndBindTypeAnnotation(node)
        val expr = node.lastChild(BaseSets.expressions)?.let { bindExpr(it) }
            ?: HIRError.Propagation.at(node.ref)

        return HIR.LetExpr(node.ref, pattern, type, expr)
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

        return HIR.ScopeExpr(node.ref, statements)
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
                val lastPack = lastPackage ?: return@map BoundImportPathSegment(segment.ref, null, null)
                lastPackage = lastPack.children[name]
                val item = astInfoMap[lastPack.path]?.accessors?.get(name)
                BoundImportPathSegment(segment.ref, lastPackage, item)
            }
            .toList()

        segments.lastOrNull()?.let { lastSeg ->
            lastSeg.node?.let { importedPackages[name] = it }
            lastSeg.item?.let { importedItems[name] = it }
        }

        return BoundImportPath(node.ref, segments)
    }
}

val HIR.Pattern.accessors get(): Pair<Accessor?, Accessor?> = getRefsFor(this, false)

fun HIR.Pattern.findId(): LocalRef? = when (this) {
    is HIR.IdentPattern -> this.local
    is HIR.MutablePattern -> this.nested?.findId()
    is HIR.WildcardPattern -> null
}

private tailrec fun getRefsFor(pattern: HIR.Pattern, mutable: Boolean): Pair<Accessor?, Accessor?> = when (pattern) {
    is HIR.IdentPattern -> {
        val first = Accessor(pattern.local, AccessorType.Get)
        val second = if (mutable) Accessor(pattern.local, AccessorType.Set) else null
        first to second
    }

    is HIR.MutablePattern -> {
        val nested = pattern.nested
        if (nested == null) null to null else getRefsFor(nested, true)
    }

    is HIR.WildcardPattern -> null to null
}