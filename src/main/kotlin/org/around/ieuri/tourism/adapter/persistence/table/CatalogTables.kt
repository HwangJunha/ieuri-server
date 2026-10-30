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
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

private val LANGUAGES = listOf("EN", "KO", "JA", "ZH")

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
