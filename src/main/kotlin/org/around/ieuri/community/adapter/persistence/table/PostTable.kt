package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PostTable : Table("community.community_post") {
    val id = long("id").autoIncrement()
    val authorId = long("author_id").index("idx_community_post_author")
    val title = varchar("title", 100)
    val body = text("body")
    val likeCount = integer("like_count").default(0)
    val commentCount = integer("comment_count").default(0)
    val hidden = bool("hidden").default(false)
    val edited = bool("edited").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val viewCount = integer("view_count").default(0)
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_community_post_feed", false, createdAt, id)
        check("ck_community_post_body_not_blank") { body.trim() neq "" }
        check("ck_community_post_title_not_blank") { title.trim() neq "" }
        check("ck_community_post_like_count") { likeCount greaterEq 0 }
        check("ck_community_post_comment_count") { commentCount greaterEq 0 }
        check("ck_community_post_view_count") { viewCount greaterEq 0 }
    }
}
