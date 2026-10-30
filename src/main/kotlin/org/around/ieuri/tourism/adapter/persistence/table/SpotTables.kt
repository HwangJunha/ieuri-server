package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

private val LANGUAGES = listOf("EN", "KO", "JA", "ZH")

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
