package org.around.ieuri.community.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
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

object PostChipTable : Table("community.community_post_chip") {
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_chip_post")
    val category = varchar("category", 30)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(postId)

    init {
        index("idx_community_post_chip_category", false, category, postId)
    }
}

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

/** `location`은 트리거가 `map_x`·`map_y`로 채운다(`ST_MakePoint`는 (lon, lat) 순서). */
object PostSpotTable : Table("community.community_post_spot") {
    val postId = long("post_id").references(PostTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_community_post_spot_post")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val mapX = double("map_x")
    val mapY = double("map_y")
    val location = geography("location").databaseGenerated()
    val catalogId = long("catalog_id")
    override val primaryKey = PrimaryKey(postId)

    init {
        index("idx_community_post_spot_catalog", false, catalogId, postId)
        index("idx_community_post_spot_location", false, location)
        check("ck_community_post_spot_map_x") { mapX.between(-180.0, 180.0) }
        check("ck_community_post_spot_map_y") { mapY.between(-90.0, 90.0) }
    }
}

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
