package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.CategoryTable
import org.around.ieuri.tourism.adapter.persistence.table.CategoryTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.DistrictTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** tourism 조회 행 → 읽기 모델. 같은 타입 열(좌표·제목/주소·코드)이 뒤바뀌지 않는지 필드마다 다른 값으로 본다. */
class TourismViewRowMappersTest {

    private val catalogRow: Map<Expression<*>, Any?> = mapOf(
        SpotCatalogTable.id to 11L,
        SpotCatalogTable.thumbnailImageUrl to "https://img/catalog.jpg",
        SpotCatalogTable.category to "RESTAURANT",
        SpotCatalogTable.copyright to "TYPE3",
        SpotCatalogTable.mapX to 127.25,
        SpotCatalogTable.mapY to 37.75,
        SpotCatalogTranslationTable.title to "광장시장",
        SpotCatalogTranslationTable.address to "종로구",
    )

    private val spotRow: Map<Expression<*>, Any?> = mapOf(
        SpotTable.id to 21L,
        SpotTable.contentId to "C-9",
        SpotTable.language to "EN",
        SpotTable.title to "Gwangjang Market",
        SpotTable.address to "Jongno",
        SpotTable.thumbnailImageUrl to "https://img/spot.jpg",
        SpotTable.mapX to 126.5,
        SpotTable.mapY to 36.5,
        SpotTable.category to "SHOPPING",
        SpotTable.copyright to "TYPE1",
    )

    @Test
    fun `카드 기본값은 경도에 map_x, 위도에 map_y를 넣고 제목·주소는 번역에서 온다`() {
        val basics = ResultRow.createAndFillValues(catalogRow).toSpotBasics()

        assertEquals("광장시장", basics.title)
        assertEquals("종로구", basics.address)
        assertEquals("https://img/catalog.jpg", basics.thumbnailImageUrl)
        assertEquals("RESTAURANT", basics.category.code)
        assertEquals(CopyrightType.TYPE3, basics.copyright)
        assertEquals(127.25, basics.longitude)
        assertEquals(37.75, basics.latitude)
    }

    @Test
    fun `주소 없는 번역은 카드 주소가 null이다`() {
        val basics = ResultRow.createAndFillValues(catalogRow + (SpotCatalogTranslationTable.address to null)).toSpotBasics()

        assertNull(basics.address)
    }

    @Test
    fun `식별 정보는 spot id와 contentId를, 좌표는 카탈로그 값을 쓴다`() {
        val identity = ResultRow.createAndFillValues(spotRow + catalogRow).toSpotIdentity()

        assertEquals(21L, identity.spotId)
        assertEquals("C-9", identity.contentId)
        assertEquals(127.25, identity.mapX)
        assertEquals(37.75, identity.mapY)
    }

    @Test
    fun `좋아요 목록 행은 카탈로그 id를 id로 내보내고 반응 갱신 시각을 좋아요 시각으로 쓴다`() {
        val liked = ResultRow.createAndFillValues(
            catalogRow + (SpotReactionTable.updatedAt to OffsetDateTime.of(2026, 10, 6, 1, 2, 3, 0, ZoneOffset.UTC)),
        ).toLikedSpotView()

        assertEquals(11L, liked.id)
        assertEquals(11L, liked.catalogId)
        assertEquals("광장시장", liked.title)
        assertEquals("종로구", liked.address)
        assertEquals("https://img/catalog.jpg", liked.thumbnailImageUrl)
        assertEquals("RESTAURANT", liked.category.code)
        assertEquals(CopyrightType.TYPE3, liked.copyright)
        assertEquals(CopyrightType.TYPE3.attributionText, liked.attributionText)
        assertEquals(Instant.parse("2026-10-06T01:02:03Z"), liked.likedAt)
    }

    @Test
    fun `배정 대상 원본은 좌표·제목·주소를 바꾸지 않는다`() {
        val spot = ResultRow.createAndFillValues(spotRow).toCatalogOrigin()

        assertEquals(21L, spot.spotId)
        assertEquals(DomainLocale.EN, spot.language)
        assertEquals("Gwangjang Market", spot.title)
        assertEquals("Jongno", spot.address)
        assertEquals("https://img/spot.jpg", spot.thumbnailImageUrl)
        assertEquals(126.5, spot.mapX)
        assertEquals(36.5, spot.mapY)
        assertEquals("SHOPPING", spot.category.code)
        assertEquals(CopyrightType.TYPE1, spot.copyright)
    }

    @Test
    fun `배정 후보는 카탈로그 id·좌표·썸네일을 옮긴다`() {
        val candidate = ResultRow.createAndFillValues(catalogRow).toCatalogCandidate()

        assertEquals(11L, candidate.catalogId)
        assertEquals(127.25, candidate.mapX)
        assertEquals(37.75, candidate.mapY)
        assertEquals("https://img/catalog.jpg", candidate.thumbnailImageUrl)
    }

    @Test
    fun `사전 수집 대상은 spot 행의 값을 바꾸지 않는다`() {
        val target = ResultRow.createAndFillValues(spotRow).toPrefetchTarget()

        assertEquals(21L, target.spotId)
        assertEquals("C-9", target.contentId)
        assertEquals(DomainLocale.EN, target.language)
        assertEquals("SHOPPING", target.category.code)
        assertEquals("Gwangjang Market", target.title)
        assertEquals("https://img/spot.jpg", target.thumbnailImageUrl)
        assertEquals(CopyrightType.TYPE1, target.copyright)
        assertEquals("Jongno", target.address)
    }

    @Test
    fun `주소 없는 spot은 배정 대상과 사전 수집 대상의 주소가 null이다`() {
        val row = ResultRow.createAndFillValues(spotRow + (SpotTable.address to null))

        assertNull(row.toCatalogOrigin().address)
        assertNull(row.toPrefetchTarget().address)
    }

    @Test
    fun `카테고리 라벨은 번역에서 오고 번역이 없으면 코드가 라벨이다`() {
        val translated = ResultRow.createAndFillValues(
            mapOf(CategoryTable.code to "RESTAURANT", CategoryTranslationTable.label to "맛집", CategoryTable.iconUrl to "https://img/icon.png"),
        )
        val untranslated = ResultRow.createAndFillValues(
            mapOf(CategoryTable.code to "SHOPPING", CategoryTranslationTable.label to null, CategoryTable.iconUrl to null),
        )

        assertEquals("맛집", translated.toCategoryDisplay().label)
        assertEquals("https://img/icon.png", translated.toCategoryDisplay().iconUrl)
        assertEquals("RESTAURANT", translated.toCategoryDisplay().category.code)
        assertEquals("SHOPPING", untranslated.toCategoryDisplay().label)
        assertEquals("SHOPPING", untranslated.toCategoryDisplay().label)
        assertNull(untranslated.toCategoryDisplay().iconUrl)
    }

    @Test
    fun `시군구 코드와 시도 코드를 바꾸지 않는다`() {
        val region = ResultRow.createAndFillValues(mapOf(DistrictTable.code to "11110", DistrictTable.areaCd to "11")).toDistrictRegion()

        assertEquals("11110", region.code)
        assertEquals("11", region.provinceCode)
    }
}
