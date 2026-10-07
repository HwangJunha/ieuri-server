package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object PostPhotoTable : Table("community.community_post_photo") {
    val id = long("id").autoIncrement()
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_photo_post")
    val objectKey = varchar("object_key", 512)
    val position = short("position")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_post_photo_position", postId, position)
        index("idx_community_post_photo_post", false, postId, position)
        check("ck_community_post_photo_position") { position.between(0.toShort(), 4.toShort()) }
    }
}
