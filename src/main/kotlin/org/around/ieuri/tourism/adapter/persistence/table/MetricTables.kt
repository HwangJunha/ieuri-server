package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.around.ieuri.common.persistence.vector
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// 지역 지표(KTO 빅데이터)·혼잡 영역·장소 임베딩. 원본은 마이그레이션이다 — 이름을 마이그레이션과 같게 둔다.

object KtoVisitorCountTable : Table("tour.kto_visitor_count") {
    val id = long("id").autoIncrement()
    val districtCode = varchar("district_code", 5)
    val baseDate = date("base_date")
    val visitorType = varchar("visitor_type", 32)
    val touristCount = decimal("tou_num", 30, 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uk_kto_visitor_count_natural", districtCode, baseDate, visitorType)
    }
}

object KtoConcentrationRateTable : Table("tour.kto_concentration_rate") {
    val id = long("id").autoIncrement()
    val districtCode = varchar("district_code", 5)
    val touristSpotName = varchar("tourist_spot_name", 256)
    val targetDate = date("target_date")
    val concentrationRate = decimal("cnctr_rate", 30, 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uk_kto_concentration_rate_natural", districtCode, touristSpotName, targetDate)
    }
}

object KtoStayIntensityTable : Table("tour.kto_stay_intensity") {
    val id = long("id").autoIncrement()
    val districtCode = varchar("district_code", 5)
    val baseMonth = date("base_month")
    val indicatorCode = varchar("indicator_cd", 32)
    val indicatorValue = decimal("indicator_value", 30, 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uk_kto_stay_intensity_natural", districtCode, baseMonth, indicatorCode)
    }
}

object KtoSpendingIntensityTable : Table("tour.kto_spending_intensity") {
    val id = long("id").autoIncrement()
    val districtCode = varchar("district_code", 5)
    val baseMonth = date("base_month")
    val indicatorCode = varchar("indicator_cd", 32)
    val indicatorValue = decimal("indicator_value", 30, 10)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uk_kto_spending_intensity_natural", districtCode, baseMonth, indicatorCode)
    }
}

object CongestionAreaTable : Table("tour.congestion_area") {
    val areaCd = varchar("area_cd", 16)
    val category = varchar("category", 32)
    val nameKo = varchar("name_ko", 128)
    val nameEn = varchar("name_en", 128)
    val nameJa = varchar("name_ja", 128).nullable()
    val nameZh = varchar("name_zh", 128).nullable()
    val boundary = geography("boundary").databaseGenerated()
    override val primaryKey = PrimaryKey(areaCd)

    init {
        index("idx_congestion_area_boundary", false, boundary)
    }
}

/** 번역 행 하나의 임베딩. [textHash]는 임베딩한 텍스트의 md5라, 텍스트가 바뀌면 다시 대상이 된다. */
object SpotEmbeddingTable : Table("tour.spot_embedding") {
    val translationId = long("translation_id")
        .references(SpotCatalogTranslationTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "spot_embedding_translation_id_fkey")
    val model = varchar("model", 50)
    val textHash = char("text_hash", 32)
    val embedding = vector("embedding")
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(translationId)
}
