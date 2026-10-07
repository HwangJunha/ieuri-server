package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PostTagTable : Table("community.community_post_tag") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_tag_post")
        .index("idx_community_post_tag_post")
    val name = varchar("name", 30)
    val normalized = varchar("normalized", 30)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_tag", postId, normalized)
        index("idx_community_post_tag_normalized", false, normalized, postId)
        check("ck_community_post_tag_name_not_blank") { name.trim() neq "" }
        check("ck_community_post_tag_normalized_not_blank") { normalized.trim() neq "" }
    }
}
