package org.around.ieuri.tourism.adapter.persistence.query

import org.around.ieuri.common.persistence.asGeometry
import org.around.ieuri.common.persistence.stX
import org.around.ieuri.common.persistence.stY
import org.around.ieuri.tourism.adapter.persistence.mapper.toCellSpotView
import org.around.ieuri.tourism.adapter.persistence.mapper.toMapCellDistrictRow
import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellDistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.application.shared.provided.dto.CellSpotView
import org.around.ieuri.tourism.application.shared.required.MapCellQueryPort
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.MapCellDistrictRow
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

/**
 * 전국 지도 칸 조회 어댑터(SPEC-0030).
 * - 칸의 최종 순서는 도메인(`MapCellGrouping.fold`)이 정한다. 정렬은 한 칸의 시군구를 붙여 놓기 위함이다.
 */
@Component
class MapCellQueryAdapter : MapCellQueryPort {

    override fun findAllCellDistricts(): List<MapCellDistrictRow> = cellDistricts(Op.TRUE)

    override fun findCellDistricts(cellId: Long): List<MapCellDistrictRow> = cellDistricts(MapCellTable.id eq cellId)

    override fun findCellDistrictsIn(cellIds: Collection<Long>): List<MapCellDistrictRow> {
        if (cellIds.isEmpty()) return emptyList()
        return cellDistricts(MapCellTable.id inList cellIds)
    }

    /**
     * 칸에 속한 장소(R11). 좋아요 내림차순, 같은 수는 id 오름차순.
     * - 활성 언어에 번역이 없는 장소는 빠지고, 좋아요 0건인 장소는 0으로 남는다.
     * - 그래서 반응은 `LEFT JOIN`이고 조건이 `ON`에 있으며 `COUNT(r.id)`다 — 하나라도 바꾸면 0건 장소가 사라지거나 1이 된다.
     */
    override fun findCellSpots(cellId: Long, language: DomainLocale, limit: Int): List<CellSpotView> {
        val likeCount = SpotReactionTable.id.count()
        val columns = listOf(
            SpotCatalogTable.id,
            SpotCatalogTranslationTable.title,
            SpotCatalogTable.thumbnailImageUrl,
            SpotCatalogTable.category,
            SpotCatalogTable.mapX,
            SpotCatalogTable.mapY,
        )
        return SpotCatalogTable
            .join(SpotCatalogTranslationTable, JoinType.INNER, SpotCatalogTranslationTable.catalogId, SpotCatalogTable.id) {
                (SpotCatalogTranslationTable.language eq language.name) and (SpotCatalogTranslationTable.visible eq true)
            }
            .join(SpotReactionTable, JoinType.LEFT, SpotReactionTable.catalogId, SpotCatalogTable.id) {
                (SpotReactionTable.reaction eq ReactionType.LIKE.name) and (SpotReactionTable.language eq language.name)
            }
            .join(MapCellDistrictTable, JoinType.INNER, MapCellDistrictTable.districtCode, SpotCatalogTable.districtCode)
            .select(columns + likeCount)
            .where { MapCellDistrictTable.cellId eq cellId }
            .groupBy(*columns.toTypedArray())
            .orderBy(likeCount to SortOrder.DESC, SpotCatalogTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toCellSpotView(likeCount = it[likeCount]) }
    }

    /** 대표 좌표는 `center`다(`representative_location`은 일부 시군구에서 null). 시도는 시군구에서 온다(한 칸의 시군구는 같은 시도). */
    private fun cellDistricts(condition: Op<Boolean>): List<MapCellDistrictRow> = MapCellTable
        .join(MapCellDistrictTable, JoinType.INNER, MapCellDistrictTable.cellId, MapCellTable.id)
        .join(DistrictTable, JoinType.INNER, DistrictTable.code, MapCellDistrictTable.districtCode)
        .join(AreaTable, JoinType.INNER, AreaTable.areaCd, DistrictTable.areaCd)
        .select(
            DistrictTable.code, MapCellDistrictTable.cellId, DistrictTable.areaCd,
            MapCellTable.nameKo, MapCellTable.nameEn, MapCellTable.nameJa, MapCellTable.nameZh,
            AreaTable.nameKo, AreaTable.nameEn, AreaTable.nameJa, AreaTable.nameZh,
            CENTER_LATITUDE, CENTER_LONGITUDE,
        )
        .where { condition }
        .orderBy(MapCellTable.id to SortOrder.ASC, DistrictTable.code to SortOrder.ASC)
        .map { it.toMapCellDistrictRow(latitude = it[CENTER_LATITUDE], longitude = it[CENTER_LONGITUDE]) }

    private companion object {
        /** geography를 SELECT 목록에서만 geometry로 바꿔 읽는다. 조건에서 바꾸면 GiST 인덱스를 못 탄다. */
        val CENTER_LATITUDE = stY(DistrictTable.center.asGeometry())
        val CENTER_LONGITUDE = stX(DistrictTable.center.asGeometry())
    }
}
