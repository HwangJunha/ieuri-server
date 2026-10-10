package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MemberTermsAgreementTable : Table("auth.member_terms_agreement") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_mta_member")
    val termsId = long("terms_id").references(TermsTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_mta_terms")
    val termsCode = varchar("terms_code", 50)
    val termsVersion = varchar("terms_version", 20)
    val agreed = bool("agreed")
    val agreedAt = timestampWithTimeZone("agreed_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_mta_member_terms", memberId, termsId)
        index("idx_mta_member", false, memberId)
    }
}
