package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// tour 스키마. 원본은 마이그레이션이다 — 컬럼·제약·인덱스 이름을 마이그레이션과 같게 둔다.
// 지역 지표·혼잡 영역·임베딩 테이블은 `MetricTables.kt`에 있다.

private val LANGUAGES = listOf("EN", "KO", "JA", "ZH")

object AreaTable : Table("tour.area") {
    val areaCd = varchar("area_cd", 2)
    val nameKo = varchar("name_ko", 128)
    val nameEn = varchar("name_en", 128)
    val nameJa = varchar("name_ja", 128).nullable()
    val nameZh = varchar("name_zh", 128).nullable()
    override val primaryKey = PrimaryKey(areaCd)
}

/** 경계·중심은 적재 데이터다. 대표 위치는 배치가 쓴다. */
object DistrictTable : Table("tour.district") {
    val code = varchar("code", 5)
    val areaCd = varchar("area_cd", 2)
        .references(AreaTable.areaCd, ReferenceOption.NO_ACTION, ReferenceOption.NO_ACTION, "fk_district_area")
        .index("idx_district_area_cd")
    val nameKo = varchar("name_ko", 128)
    val nameEn = varchar("name_en", 128)
    val nameJa = varchar("name_ja", 128).nullable()
    val nameZh = varchar("name_zh", 128).nullable()
    val boundary = geography("boundary").databaseGenerated()
    val center = geography("center").databaseGenerated()
    val representativeLocation = geography("representative_location").nullable()
    val representativeSpotCount = integer("representative_spot_count").nullable()
    val representativeUpdatedAt = timestampWithTimeZone("representative_updated_at").nullable()
    override val primaryKey = PrimaryKey(code)

    init {
        index("idx_district_boundary", false, boundary)
        index("idx_district_representative_location", false, representativeLocation, filterCondition = { representativeLocation.isNotNull() })
    }
}

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

object MapRelationTable : Table("tour.map_relation") {
    val id = long("id").autoIncrement()
    val ownerId = long("owner_id")
    val counterpartId = long("counterpart_id").index("idx_map_relation_counterpart")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_map_relation", ownerId, counterpartId)
        index("idx_map_relation_owner_created", false, ownerId, createdAt, counterpartId)
        check("ck_map_relation_not_self") { ownerId neq counterpartId }
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

/** `location`은 트리거가 `map_x`·`map_y`로 채운다(`ST_MakePoint`는 (lon, lat) 순서). */
object SpotCatalogTable : Table("tour.spot_catalog") {
    val id = long("id").autoIncrement()
    val mapX = double("map_x")
    val mapY = double("map_y")
    val location = geography("location").databaseGenerated()
    val thumbnailImageUrl = varchar("thumbnail_image_url", 500)
    val category = varchar("category", 30).index("idx_spot_catalog_category")
    val copyright = varchar("copyright", 20)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val districtCode = varchar("district_code", 5).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_spot_catalog_district", false, districtCode, filterCondition = { districtCode.isNotNull() })
        index("idx_spot_catalog_location", false, location)
        check("ck_spot_catalog_coordinates_in_korea") { mapX.between(124.0, 132.0) and mapY.between(33.0, 39.0) }
    }
}

/** `location`은 트리거가 `map_x`·`map_y`로 채운다. */
object SpotCatalogTranslationTable : Table("tour.spot_catalog_translation") {
    val id = long("id").autoIncrement()
    val catalogId = long("catalog_id")
        .references(SpotCatalogTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_catalog_translation_catalog")
        .index("idx_spot_catalog_translation_catalog")
    val language = varchar("language", 2).index("idx_spot_catalog_translation_language")
    val title = varchar("title", 255)
    val address = varchar("address", 500).nullable()
    val mapX = double("map_x")
    val mapY = double("map_y")
    val location = geography("location").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val category = varchar("category", 30)
    val visible = bool("visible").default(true)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_catalog_translation", catalogId, language)
        index("idx_spot_catalog_translation_language_category", false, language, category)
        index("idx_spot_catalog_translation_location", false, location, filterCondition = { visible eq true })
        index("idx_spot_catalog_translation_visible", false, visible, language)
        check("ck_spot_catalog_translation_language") { language inList LANGUAGES }
        check("ck_spot_catalog_translation_title_not_blank") { title.trim() neq "" }
    }
}

object SpotCatalogFestivalTable : Table("tour.spot_catalog_festival") {
    val catalogId = long("catalog_id")
        .references(SpotCatalogTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_catalog_festival_catalog")
    val eventStartDate = date("event_start_date")
    val eventEndDate = date("event_end_date").index("idx_spot_catalog_festival_end_date")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(catalogId)

    init {
        check("ck_spot_catalog_festival_period") { eventEndDate greaterEq eventStartDate }
    }
}

/** 언어별 TourAPI 원본 행. `location`은 트리거가 채운다. 카탈로그가 지워지면 `catalog_id`가 null이 된다. */
object SpotTable : Table("tour.spot") {
    val id = long("id").autoIncrement()
    val contentId = varchar("content_id", 32)
    val language = varchar("language", 2)
    val title = varchar("title", 255)
    val address = varchar("address", 500).nullable()
    val thumbnailImageUrl = varchar("thumbnail_image_url", 500)
    val mapX = double("map_x")
    val mapY = double("map_y")
    val category = varchar("category", 30)
    val contentTypeId = integer("content_type_id")
    val copyright = varchar("copyright", 20)
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val location = geography("location").databaseGenerated()
    val catalogId = long("catalog_id")
        .references(SpotCatalogTable.id, ReferenceOption.SET_NULL, ReferenceOption.NO_ACTION, "fk_spot_catalog")
        .nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot", contentId, language)
        index("idx_spot_catalog_id", false, catalogId, filterCondition = { catalogId.isNotNull() })
        index("idx_spot_catalog_language", false, catalogId, language)
        index("idx_spot_language_category", false, language, category)
        index("idx_spot_location", false, location)
        check("ck_spot_language") { language inList LANGUAGES }
        check("ck_spot_map_x") { mapX.between(-180.0, 180.0) }
        check("ck_spot_map_y") { mapY.between(-90.0, 90.0) }
        check("ck_spot_thumbnail_not_blank") { thumbnailImageUrl.trim() neq "" }
    }
}

object SpotFestivalTable : Table("tour.spot_festival") {
    val id = long("id").autoIncrement()
    val eventStartDate = date("event_start_date")
    val eventEndDate = date("event_end_date").index("idx_spot_festival_end_date")
    val progressType = varchar("progress_type", 50).nullable()
    val festivalType = varchar("festival_type", 50).nullable()
    val lastSyncedAt = timestampWithTimeZone("last_synced_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val spotId = long("spot_id")
        .references(SpotTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_festival_spot")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_festival", spotId)
        check("ck_spot_festival_period") { eventEndDate greaterEq eventStartDate }
    }
}

object SpotDetailTable : Table("tour.spot_detail") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val category = varchar("category", 30)
    val thumbnailImageUrl = varchar("thumbnail_image_url", 500)
    val overview = text("overview").nullable()
    val address = varchar("address", 500).nullable()
    val homepage = text("homepage").nullable()
    val tel = varchar("tel", 255).nullable()
    val copyright = varchar("copyright", 20)
    val fetchedAt = timestampWithTimeZone("fetched_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val spotId = long("spot_id")
        .references(SpotTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_detail_spot")
    val bodyAbsent = bool("body_absent").default(false)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_detail", spotId)
    }
}

object SpotDetailImageTable : Table("tour.spot_detail_image") {
    val id = long("id").autoIncrement()
    val displayOrder = integer("display_order")
    val imageUrl = varchar("image_url", 500)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val caption = varchar("caption", 500).nullable()
    val copyright = varchar("copyright", 20)
    val fetchedAt = timestampWithTimeZone("fetched_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val spotId = long("spot_id")
        .references(SpotTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_detail_image_spot")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_detail_image", spotId, displayOrder)
    }
}

object SpotDetailInfoTable : Table("tour.spot_detail_info") {
    val id = long("id").autoIncrement()
    val category = varchar("category", 30)
    val displayOrder = integer("display_order")
    val name = varchar("name", 255)
    val text = text("text")
    val fetchedAt = timestampWithTimeZone("fetched_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val spotId = long("spot_id")
        .references(SpotTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_detail_info_spot")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_detail_info", spotId, category, displayOrder)
    }
}

object SpotDetailIntroTable : Table("tour.spot_detail_intro") {
    val id = long("id").autoIncrement()
    val category = varchar("category", 30)
    val businessHours = text("business_hours").nullable()
    val closedDays = text("closed_days").nullable()
    val parking = text("parking").nullable()
    val inquiryPhone = text("inquiry_phone").nullable()
    val fee = text("fee").nullable()
    val petAllowed = text("pet_allowed").nullable()
    val creditCardAccepted = text("credit_card_accepted").nullable()
    val fetchedAt = timestampWithTimeZone("fetched_at").databaseGenerated()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val spotId = long("spot_id")
        .references(SpotTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_detail_intro_spot")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_detail_intro", spotId, category)
    }
}

object SpotReactionTable : Table("tour.spot_reaction") {
    val id = long("id").autoIncrement()
    val memberId = long("member_id").index("idx_spot_reaction_member")
    val reaction = varchar("reaction", 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val catalogId = long("catalog_id")
        .references(SpotCatalogTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_spot_reaction_catalog")
        .index("idx_spot_reaction_catalog_id")
    val language = varchar("language", 2)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_spot_reaction", memberId, catalogId, language)
        index("idx_spot_reaction_catalog_reaction", false, catalogId, language, reaction)
        index("idx_spot_reaction_liked_list", false, memberId, language, reaction, updatedAt, catalogId)
        check("ck_spot_reaction_language") { language inList LANGUAGES }
        check("ck_spot_reaction_reaction") { reaction inList listOf("LIKE", "DISLIKE") }
    }
}
