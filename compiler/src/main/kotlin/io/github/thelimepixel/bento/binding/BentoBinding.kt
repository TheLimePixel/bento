package io.github.thelimepixel.bento.binding

import io.github.thelimepixel.bento.parsing.*

private typealias BC = BindingContext
typealias ST = SyntaxType
private typealias LC = LocalBindingContext

class BentoBinding {
    fun bind(
        info: PackageASTInfo,
        importData: BoundImportData,
        parentContext: BindingContext,
    ): Map<ItemRef, HIR.Def> {
        val initialized = mutableSetOf<ItemRef>()
        val context = FileBindingContext(
            parentContext,
            info.items.asSequence().filter { it.type.immutable }.associateBy { it.name } + importData.immutableItems,
            info.items.asSequence().filter { it.type.mutable }.associateBy { it.name } + importData.mutableItems,
            initialized
        )
        return info.items.associateWith { ref ->
            context.bindDefinition(info.dataMap[ref.name]!![ref.index].toRedRoot())
                .also { initialized.add(ref) }
        }
    }

    private fun BC.bindDefinition(node: RedNode): HIR.Def = when (node.type) {
        ST.FunDef -> bindFunctionLike(node, HIR::FunctionDef)
        ST.GetDef -> bindFunctionLike(node, HIR::GetterDef)
        ST.SetDef -> bindFunctionLike(node, HIR::SetterDef)
        ST.LetDef -> bindLet(node)
        else -> error("Unsupported definition type")
    }

    private fun BC.findAndBindTypeAnnotation(node: RedNode): HIR.TypeRef? = node
        .firstChild(SyntaxType.TypeAnnotation)
        ?.firstChild(SyntaxType.Identifier)
        ?.let {
            val itemRef = refForImmutable(it.rawContent)
            if (itemRef is ItemRef && itemRef.type == ItemType.Type)
                HIR.TypeRef(it.ref, itemRef.path)
            else null
        }

    private fun findAndBindPattern(node: RedNode): HIR.Pattern = node.firstChild(BaseSets.patterns)?.let {
        when (it.type) {
            ST.IdentPattern -> HIR.IdentPattern(it.ref, it.rawContent)
            ST.WildcardPattern -> HIR.WildcardPattern(it.ref)
            else -> error("Unsupported pattern type: ${it.type}")
        }
    } ?: HIRError.Propagation.at(node.ref)

    private fun BC.bindParamList(node: RedNode): List<HIR.Param> = node.firstChild(SyntaxType.ParamList)
        ?.childSequence()
        ?.filter { it.type == SyntaxType.Param }
        ?.map {
            val name = findAndBindPattern(it)
            val type = findAndBindTypeAnnotation(it)
            HIR.Param(it.ref, name, type)
        }
        ?.toList()
        ?: emptyList()

    private inline fun BC.bindFunctionLike(
        node: RedNode, ctor: (
            ref: ASTRef, params: List<HIR.Param>, returnType: HIR.TypeRef?, body: HIR.ScopeExpr?
        ) -> HIR.FunctionLikeDef
    ): HIR.FunctionLikeDef {
        val params = bindParamList(node)
        val returnType = findAndBindTypeAnnotation(node)
        val context = FunctionBindingContext(
            this, params.asSequence().mapNotNull { it.pattern as? HIR.IdentPattern }
                .associateBy({ it.name }, { LocalRef((it)) })
        )
        val body = node.lastChild(SyntaxType.ScopeExpr)?.let { context.bindScope(it) }

        return ctor(node.ref, params, returnType, body)
    }

    private fun BC.bindLet(node: RedNode): HIR.ConstantDef {
        val type = findAndBindTypeAnnotation(node)
        val context = LocalBindingContext(this)
        val body = node.lastChild(BaseSets.expressions)?.let { context.bindExpr(it) }
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

    private fun BC.bindIdentifier(node: RedNode) = refForImmutable(node.rawContent)
        ?.let {
            if (isInitialized(it)) HIR.IdentExpr(node.ref, it)
            else HIR.ErrorExpr(node.ref, HIRError.UninitializedConstant)
        } ?: HIR.ErrorExpr(node.ref, HIRError.UnboundIdentifier)

    private fun LC.bindExpr(node: RedNode): HIR.Expr = when (node.type) {
        ST.StringLiteral -> HIR.StringExpr(node.ref, node.content)
        ST.IdentExpr -> bindIdentifier(node)
        ST.CallExpr -> bindCall(node)
        ST.ScopeExpr -> bindScope(node)
        ST.LetExpr -> bindLet(node)
        ST.ParenthesizedExpr -> bindParenthesizedExpr(node)
        ST.AssignmentExpr -> bindAssignmentExpr(node)
        else -> HIR.ErrorExpr(node.ref, HIRError.Propagation)
    }

    private fun LC.bindAssignmentExpr(node: RedNode): HIR.Expr {
        val leftRef = node.firstChild(BaseSets.expressions)?.let { expr ->
            if (expr.type == ST.IdentExpr) refForMutable(expr.rawContent)
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

    private fun BC.bindScope(node: RedNode): HIR.ScopeExpr {
        val context = LocalBindingContext(this)
        val statements = node
            .childSequence()
            .filter { it.type in BaseSets.expressions }
            .map { context.bindExpr(it) }
            .toList()

        return HIR.ScopeExpr(node.ref, statements)
    }

    fun bindImport(
        node: GreenNode?,
        rootPackage: PackageTreeNode,
        items: Map<ItemPath, PackageASTInfo>
    ): BoundImportData {
        val block = node?.toRedRoot()?.firstChild(ST.ImportBlock) ?: return emptyImportData
        val importedMutableItems = mutableMapOf<String, ItemRef>()
        val importedImmutableItems = mutableMapOf<String, ItemRef>()
        val importedPackages = mutableMapOf<String, PackageTreeNode>()
        val paths = block.childSequence()
            .filter { it.type == ST.ImportPath }
            .map {
                bindImportPath(it, importedMutableItems, importedImmutableItems, importedPackages, rootPackage, items)
            }
            .toList()

        return BoundImportData(importedMutableItems, importedImmutableItems, importedPackages, paths)
    }

    private fun bindImportPath(
        node: RedNode,
        importedMutableItems: MutableMap<String, ItemRef>,
        importedImmutableItems: MutableMap<String, ItemRef>,
        importedPackages: MutableMap<String, PackageTreeNode>,
        rootPackage: PackageTreeNode,
        packageItems: Map<ItemPath, PackageASTInfo>
    ): BoundImportPath {
        var lastPackage: PackageTreeNode? = rootPackage
        var name = ""
        val segments = node.childSequence()
            .filter { it.type == ST.Identifier }
            .map { segment ->
                name = segment.rawContent
                val lastPack = lastPackage ?: return@map BoundImportPathSegment(segment.ref, null, emptyList())
                lastPackage = lastPack.children[name]
                val items =
                    packageItems[lastPack.path]?.items?.filter { it.path == lastPack.path.subpath(name) } ?: emptyList()
                BoundImportPathSegment(segment.ref, lastPackage, items)
            }
            .toList()

        segments.lastOrNull()?.let { lastSeg ->
            lastSeg.node?.let { importedPackages[name] = it }
            lastSeg.items.forEach {
                (if (it.type.mutable) importedMutableItems else importedImmutableItems)[name] = it
            }
        }

        return BoundImportPath(node.ref, segments)
    }
}