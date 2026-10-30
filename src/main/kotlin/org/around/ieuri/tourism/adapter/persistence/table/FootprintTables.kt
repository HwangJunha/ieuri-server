package org.around.ieuri.tourism.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MapCellTable : Table("tour.map_cell") {
    val id = long("id").autoIncrement()
    val nameKo = varchar("name_ko", 128)
    val nameEn = varchar("name_en", 128)
    val nameJa = varchar("name_ja", 128).nullable()
    val nameZh = varchar("name_zh", 128).nullable()
    override val primaryKey = PrimaryKey(id)
}

object MapCellDistrictTable : Table("tour.map_cell_district") {
    val cellId = long("cell_id")
        .references(MapCellTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_map_cell_district_cell")
    val districtCode = varchar("district_code", 5)
        .references(DistrictTable.code, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_map_cell_district_district")
        .index("idx_map_cell_district_district")
    override val primaryKey = PrimaryKey(cellId, districtCode)
}

object MapCellImageTable : Table("tour.map_cell_image") {
    val id = long("id").autoIncrement()
    val cellId = long("cell_id")
        .references(MapCellTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_map_cell_image_cell")
    val displayOrder = integer("display_order")
    val imageUrl = varchar("image_url", 500)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val caption = varchar("caption", 255)
    val kind = varchar("kind", 20)
    val copyright = varchar("copyright", 20)
    val contentId = varchar("content_id", 20).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_map_cell_image_order", cellId, displayOrder)
        check("ck_map_cell_image_copyright") { copyright inList listOf("TYPE1", "TYPE3", "UNSPECIFIED") }
        check("ck_map_cell_image_display_order") { displayOrder greaterEq 0 }
        check("ck_map_cell_image_kind") { kind inList listOf("LANDMARK", "SPECIALTY") }
    }
}

object MemberDistrictVisitTable : Table("tour.member_district_visit") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val districtCode = varchar("district_code", 5)
        .references(DistrictTable.code, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_member_district_visit_district")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_member_district_visit", memberId, districtCode)
    }
}

/** 회원당 살아 있는(revoked_at이 null인) 링크는 하나다(부분 유일 인덱스). */
object MapShareLinkTable : Table("tour.map_share_link") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id")
    val token = varchar("token", 64)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_map_share_link_token", token)
        uniqueIndex("uq_map_share_link_active_member", memberId, filterCondition = { revokedAt.isNull() })
    }
}
