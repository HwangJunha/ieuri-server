package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PostLikeTable : Table("community.community_post_like") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_like_post")
    val memberId = long("member_id")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_like", postId, memberId)
        index("idx_community_post_like_member", false, memberId, postId)
    }
}
