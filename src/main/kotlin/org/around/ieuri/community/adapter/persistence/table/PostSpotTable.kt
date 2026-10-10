package org.around.ieuri.community.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
