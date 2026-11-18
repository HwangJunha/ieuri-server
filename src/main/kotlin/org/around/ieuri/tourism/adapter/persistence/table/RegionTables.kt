package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

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
