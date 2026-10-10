package org.around.ieuri.auth.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MemberProfileTable : Table("auth.member_profile") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id").references(MemberTable.id, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_member_profile_member")
    val nickname = varchar("nickname", 50)
    val profileImageUrl = varchar("profile_image_url", 1024).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_member_profile_member", memberId)
    }
}
