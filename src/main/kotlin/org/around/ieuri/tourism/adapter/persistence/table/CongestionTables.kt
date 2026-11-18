package org.around.ieuri.tourism.adapter.persistence.table

import org.around.ieuri.common.persistence.geography
import org.jetbrains.exposed.v1.core.Table

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
