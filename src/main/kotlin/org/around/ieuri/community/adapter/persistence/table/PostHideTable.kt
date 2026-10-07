package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PostHideTable : Table("community.community_post_hide") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_hide_post")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_hide", memberId, postId)
        index("idx_community_post_hide_member", false, memberId, createdAt, postId)
    }
}
