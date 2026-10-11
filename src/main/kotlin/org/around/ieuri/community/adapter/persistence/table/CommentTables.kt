package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object CommentTable : Table("community.community_comment") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_comment_post")
    val authorId = long("author_id").index("idx_community_comment_author")
    val body = text("body")
    val hidden = bool("hidden").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val parentCommentId = long("parent_comment_id")
        .references(id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_comment_parent")
        .nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_comment_post", false, postId, createdAt, id)
        index("idx_community_comment_parent", false, parentCommentId, createdAt, id, filterCondition = { parentCommentId.isNotNull() })
        check("ck_community_comment_body_not_blank") { body.trim() neq "" }
        check("ck_community_comment_parent_not_self") { parentCommentId.isNull() or (parentCommentId neq id) }
    }
}
