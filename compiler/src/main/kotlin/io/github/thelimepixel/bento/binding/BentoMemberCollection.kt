package io.github.thelimepixel.bento.binding

class BentoMemberCollection {
    fun collectMembers(ref: ItemRef, hir: HIR.Def): Map<String, MemberRef> =
        if (ref.type == ItemType.RecordType) collectField(ref, hir as HIR.RecordType)
        else emptyMap()

    private fun collectField(ref: ItemRef, hir: HIR.RecordType): Map<String, MemberRef> =
        hir.constructor.fields.associateBy(HIR.Field::ident) { field ->
            MemberRef(ref, field.ident, field.type?.type)
        }
}