package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.AreaTable
import org.around.ieuri.tourism.adapter.persistence.table.CongestionAreaTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellDistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.MapCellTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 공간 조회 행 → 읽기 모델. 위도·경도, 칸 이름·시도 이름처럼 같은 타입 열이 뒤바뀌지 않는지 다른 값으로 본다. */
class TourismSpatialRowMappersTest {

    private val provinceNames = mapOf<Expression<*>, Any?>(
        AreaTable.nameKo to "서울특별시",
        AreaTable.nameEn to "Seoul",
        AreaTable.nameJa to "ソウル",
        AreaTable.nameZh to "首爾",
    )

    private val catalogCard = mapOf<Expression<*>, Any?>(
        SpotCatalogTable.id to 11L,
        SpotCatalogTranslationTable.title to "광장시장",
        SpotCatalogTable.thumbnailImageUrl to "https://img/c.jpg",
        SpotCatalogTable.category to "SHOPPING",
        SpotCatalogTable.mapX to 126.99,
        SpotCatalogTable.mapY to 37.57,
    )

    @Test
    fun `혼잡 구역 이름은 네 언어가 서로 바뀌지 않고, 없는 언어는 null이다`() {
        val name = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(
                CongestionAreaTable.nameKo to "광화문",
                CongestionAreaTable.nameEn to "Gwanghwamun",
                CongestionAreaTable.nameJa to null,
                CongestionAreaTable.nameZh to "光化門",
            ),
        ).toCongestionAreaName()

        assertEquals("광화문", name.ko)
        assertEquals("Gwanghwamun", name.en)
        assertNull(name.ja)
        assertEquals("光化門", name.zh)
    }

    @Test
    fun `칸·시군구 행은 칸 이름과 시도 이름을 섞지 않고 중심의 위도·경도를 바꾸지 않는다`() {
        val row = ResultRow.createAndFillValues(
            provinceNames + mapOf(
                MapCellDistrictTable.cellId to 3L,
                DistrictTable.code to "11110",
                DistrictTable.areaCd to "11",
                MapCellTable.nameKo to "종로",
                MapCellTable.nameEn to "Jongno",
                MapCellTable.nameJa to "鍾路(ja)",
                MapCellTable.nameZh to "鍾路",
            ),
        ).toMapCellDistrictRow(latitude = 37.57, longitude = 126.98)

        assertEquals(3L, row.cellId)
        assertEquals("11110", row.districtCode)
        assertEquals("11", row.provinceCode)
        assertEquals("종로", row.cellName.ko)
        assertEquals("Jongno", row.cellName.en)
        assertEquals("鍾路(ja)", row.cellName.ja)
        assertEquals("鍾路", row.cellName.zh)
        assertEquals("서울특별시", row.provinceName.ko)
        assertEquals("Seoul", row.provinceName.en)
        assertEquals("ソウル", row.provinceName.ja)
        assertEquals("首爾", row.provinceName.zh)
        assertEquals(37.57, row.center.latitude)
        assertEquals(126.98, row.center.longitude)
    }

    @Test
    fun `추천 시군구 행은 대표 위치의 위도·경도를 쓰고 이름과 시도 이름을 섞지 않는다`() {
        val region = ResultRow.createAndFillValues(
            provinceNames + mapOf(
                DistrictTable.code to "11110",
                DistrictTable.areaCd to "11",
                DistrictTable.nameKo to "종로구",
                DistrictTable.nameEn to "Jongno-gu",
                DistrictTable.nameJa to null,
                DistrictTable.nameZh to null,
            ),
        ).toRegionRow(latitude = 37.58, longitude = 126.99)

        assertEquals("11110", region.code)
        assertEquals("11", region.provinceCode)
        assertEquals("종로구", region.name.ko)
        assertEquals("Jongno-gu", region.name.en)
        assertNull(region.name.ja)
        assertNull(region.name.zh)
        assertEquals("서울특별시", region.provinceName.ko)
        assertEquals(37.58, region.center.latitude)
        assertEquals(126.99, region.center.longitude)
    }

    @Test
    fun `칸 장소는 경도를 longitude에, 위도를 latitude에 둔다`() {
        val spot = ResultRow.createAndFillValues(catalogCard).toCellSpotView(likeCount = 4)

        assertEquals(11L, spot.id)
        assertEquals("광장시장", spot.title)
        assertEquals("https://img/c.jpg", spot.thumbnailImageUrl)
        assertEquals("SHOPPING", spot.categoryCode)
        assertEquals(126.99, spot.coordinates.longitude)
        assertEquals(37.57, spot.coordinates.latitude)
        assertEquals(4L, spot.likeCount)
    }

    @Test
    fun `지도 마커와 추천 후보는 경도를 lng에, 위도를 lat에 둔다`() {
        val row = ResultRow.createAndFillValues(catalogCard)
        val marker = row.toMapSpotView()
        val candidate = row.toMapCandidateView(distanceMeters = 812.5)

        assertEquals(11L, marker.id)
        assertEquals("광장시장", marker.title)
        assertEquals("https://img/c.jpg", marker.thumbnailImageUrl)
        assertEquals("SHOPPING", marker.category.code)
        assertEquals(126.99, marker.lng)
        assertEquals(37.57, marker.lat)
        assertEquals(126.99, candidate.lng)
        assertEquals(37.57, candidate.lat)
        assertEquals(812.5, candidate.distanceMeters)
    }

    @Test
    fun `반경 조회 행은 번역 열을 거리순 서브쿼리에서, 썸네일·저작권을 카탈로그에서 읽는다`() {
        val t = SpotCatalogTranslationTable
        val ranked = t.select(t.catalogId, t.title, t.address, t.mapX, t.mapY, t.category).alias("ranked")
        val result = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(
                ranked[t.catalogId] to 11L,
                ranked[t.title] to "광장시장",
                ranked[t.address] to null,
                ranked[t.mapX] to 126.99,
                ranked[t.mapY] to 37.57,
                ranked[t.category] to "SHOPPING",
                SpotCatalogTable.thumbnailImageUrl to "https://img/c.jpg",
                SpotCatalogTable.copyright to "TYPE3",
            ),
        ).toSpotView(ranked, distanceMeters = 42.0)

        assertEquals(11L, result.id)
        assertEquals("광장시장", result.title)
        assertNull(result.address)
        assertEquals("https://img/c.jpg", result.thumbnailImageUrl)
        assertEquals(126.99, result.mapX)
        assertEquals(37.57, result.mapY)
        assertEquals("SHOPPING", result.category.code)
        assertEquals(CopyrightType.TYPE3, result.copyright)
        assertEquals(42.0, result.distanceMeters)
    }
}
