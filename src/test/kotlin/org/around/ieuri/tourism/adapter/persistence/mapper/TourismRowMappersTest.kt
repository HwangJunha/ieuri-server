package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.MapCellImageTable
import org.around.ieuri.tourism.adapter.persistence.table.MapShareLinkTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.CatalogTranslation
import org.around.ieuri.tourism.domain.reaction.ReactionType
import org.around.ieuri.tourism.domain.reaction.SpotReaction
import org.around.ieuri.tourism.domain.shared.CellImageKind
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** tourism 행 ↔ 도메인·읽기 모델. 같은 타입 열끼리 뒤바뀌지 않는지 필드마다 다른 값으로 본다. 쓴 값은 내부 API인 `values`로만 읽힌다. */
@OptIn(InternalApi::class)
class TourismRowMappersTest {

    private val at = OffsetDateTime.of(2026, 10, 6, 1, 2, 3, 0, ZoneOffset.UTC)

    private fun row(table: Table) = InsertStatement<Number>(table)

    @Test
    fun `반응 쓰기는 회원·카탈로그를 바꾸지 않고 enum은 이름으로 쓴다`() {
        val row = row(SpotReactionTable)
        SpotReaction(memberId = 1L, catalogId = 2L, language = DomainLocale.ZH, reaction = ReactionType.LIKE).writeTo(row)

        assertEquals(1L, row.values[SpotReactionTable.memberId])
        assertEquals(2L, row.values[SpotReactionTable.catalogId])
        assertEquals("ZH", row.values[SpotReactionTable.language])
        assertEquals("LIKE", row.values[SpotReactionTable.reaction])
    }

    @Test
    fun `카탈로그 쓰기는 경도를 map_x에, 위도를 map_y에 쓴다`() {
        val row = row(SpotCatalogTable)
        standard().writeTo(row)

        assertEquals(127.5, row.values[SpotCatalogTable.mapX])
        assertEquals(37.5, row.values[SpotCatalogTable.mapY])
        assertEquals("https://img/thumb.jpg", row.values[SpotCatalogTable.thumbnailImageUrl])
        assertEquals("RESTAURANT", row.values[SpotCatalogTable.category])
        assertEquals("TYPE3", row.values[SpotCatalogTable.copyright])
    }

    @Test
    fun `번역 쓰기는 카탈로그의 좌표·분류를 사본으로 심고 보이는 상태로 쓴다`() {
        val row = row(SpotCatalogTranslationTable)
        CatalogTranslation(title = "경복궁", address = "종로구").writeTo(row, catalogId = 9L, language = DomainLocale.KO, standard = standard())

        assertEquals(9L, row.values[SpotCatalogTranslationTable.catalogId])
        assertEquals("KO", row.values[SpotCatalogTranslationTable.language])
        assertEquals("경복궁", row.values[SpotCatalogTranslationTable.title])
        assertEquals("종로구", row.values[SpotCatalogTranslationTable.address])
        assertEquals(127.5, row.values[SpotCatalogTranslationTable.mapX])
        assertEquals(37.5, row.values[SpotCatalogTranslationTable.mapY])
        assertEquals("RESTAURANT", row.values[SpotCatalogTranslationTable.category])
        assertEquals(true, row.values[SpotCatalogTranslationTable.visible])
    }

    @Test
    fun `주소가 없는 번역은 주소 열이 null이다`() {
        val row = row(SpotCatalogTranslationTable)
        CatalogTranslation(title = "경복궁", address = null).writeTo(row, catalogId = 9L, language = DomainLocale.KO, standard = standard())

        assertNull(row.values[SpotCatalogTranslationTable.address])
    }

    @Test
    fun `상세 본문 행의 각 열이 같은 이름의 필드로 간다`() {
        val detail = ResultRow.createAndFillValues(
            mapOf(
                SpotDetailTable.title to "경복궁",
                SpotDetailTable.category to "TOURIST_SPOT",
                SpotDetailTable.thumbnailImageUrl to "https://img/thumb.jpg",
                SpotDetailTable.overview to "개요",
                SpotDetailTable.address to "주소",
                SpotDetailTable.homepage to "https://home",
                SpotDetailTable.tel to "02-000",
                SpotDetailTable.copyright to "TYPE1",
            ),
        ).toSpotDetail()

        assertEquals("경복궁", detail.title)
        assertEquals("TOURIST_SPOT", detail.category.code)
        assertEquals("https://img/thumb.jpg", detail.thumbnailImageUrl)
        assertEquals("개요", detail.overview)
        assertEquals("주소", detail.address)
        assertEquals("https://home", detail.homepage)
        assertEquals("02-000", detail.tel)
        assertEquals(CopyrightType.TYPE1, detail.copyright)
        assertEquals(0L, detail.likeCount)
    }

    @Test
    fun `본문 부재 행은 선택 열이 모두 null이다`() {
        val detail = ResultRow.createAndFillValues(
            mapOf(
                SpotDetailTable.title to "경복궁",
                SpotDetailTable.category to "TOURIST_SPOT",
                SpotDetailTable.thumbnailImageUrl to "https://img/thumb.jpg",
                SpotDetailTable.overview to null,
                SpotDetailTable.address to null,
                SpotDetailTable.homepage to null,
                SpotDetailTable.tel to null,
                SpotDetailTable.copyright to "UNSPECIFIED",
            ),
        ).toSpotDetail()

        assertNull(detail.overview)
        assertNull(detail.address)
        assertNull(detail.homepage)
        assertNull(detail.tel)
    }

    @Test
    fun `갤러리 이미지는 원본과 썸네일 URL을 바꾸지 않는다`() {
        val image = ResultRow.createAndFillValues(
            mapOf(
                SpotDetailImageTable.imageUrl to "https://img/origin.jpg",
                SpotDetailImageTable.thumbnailUrl to "https://img/small.jpg",
                SpotDetailImageTable.caption to "설명",
                SpotDetailImageTable.copyright to "TYPE3",
            ),
        ).toSpotImage()

        assertEquals("https://img/origin.jpg", image.imageUrl)
        assertEquals("https://img/small.jpg", image.thumbnailUrl)
        assertEquals("설명", image.caption)
        assertEquals(CopyrightType.TYPE3, image.copyright)
    }

    @Test
    fun `운영 정보의 일곱 열이 서로 바뀌지 않는다`() {
        val intro = ResultRow.createAndFillValues(
            mapOf(
                SpotDetailIntroTable.businessHours to "09-18",
                SpotDetailIntroTable.closedDays to "월",
                SpotDetailIntroTable.parking to "가능",
                SpotDetailIntroTable.inquiryPhone to "02-111",
                SpotDetailIntroTable.fee to "무료",
                SpotDetailIntroTable.petAllowed to "불가",
                SpotDetailIntroTable.creditCardAccepted to "가능(카드)",
            ),
        ).toSpotIntro()

        assertEquals("09-18", intro.businessHours)
        assertEquals("월", intro.closedDays)
        assertEquals("가능", intro.parking)
        assertEquals("02-111", intro.inquiryPhone)
        assertEquals("무료", intro.fee)
        assertEquals("불가", intro.petAllowed)
        assertEquals("가능(카드)", intro.creditCardAccepted)
    }

    @Test
    fun `값이 하나도 없는 운영 정보 행은 비어 있다`() {
        val intro = ResultRow.createAndFillValues(
            listOf(
                SpotDetailIntroTable.businessHours,
                SpotDetailIntroTable.closedDays,
                SpotDetailIntroTable.parking,
                SpotDetailIntroTable.inquiryPhone,
                SpotDetailIntroTable.fee,
                SpotDetailIntroTable.petAllowed,
                SpotDetailIntroTable.creditCardAccepted,
            ).associateWith { null },
        ).toSpotIntro()

        assertTrue(intro.isEmpty())
    }

    @Test
    fun `이용안내 항목은 이름과 본문을 바꾸지 않는다`() {
        val item = ResultRow.createAndFillValues(mapOf(SpotDetailInfoTable.name to "입장료", SpotDetailInfoTable.text to "3,000원")).toSpotInfoItem()

        assertEquals("입장료", item.name)
        assertEquals("3,000원", item.text)
    }

    @Test
    fun `공유 링크 행은 회원·토큰·발급 시각을 옮긴다`() {
        val record = ResultRow.createAndFillValues(
            mapOf(MapShareLinkTable.memberId to 7L, MapShareLinkTable.token to "tok", MapShareLinkTable.createdAt to at),
        ).toMapShareLinkRecord()

        assertEquals(7L, record.memberId)
        assertEquals("tok", record.token)
        assertEquals(Instant.parse("2026-10-06T01:02:03Z"), record.createdAt)
    }

    @Test
    fun `칸 대표 이미지는 출처 문구를 저작권 유형에서 만든다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                MapCellImageTable.imageUrl to "https://img/cell.jpg",
                MapCellImageTable.thumbnailUrl to "https://img/cell-small.jpg",
                MapCellImageTable.caption to "남산",
                MapCellImageTable.kind to "SPECIALTY",
                MapCellImageTable.copyright to "TYPE3",
            ),
        ).toCellImageView()

        assertEquals("https://img/cell.jpg", view.imageUrl)
        assertEquals("https://img/cell-small.jpg", view.thumbnailUrl)
        assertEquals("남산", view.caption)
        assertEquals(CellImageKind.SPECIALTY, view.kind)
        assertEquals(CopyrightType.TYPE3, view.copyright)
        assertEquals(CopyrightType.TYPE3.attributionText, view.attributionText)
    }

    @Test
    fun `썸네일이 없는 칸 이미지는 썸네일이 null이다`() {
        val view = ResultRow.createAndFillValues(
            mapOf(
                MapCellImageTable.imageUrl to "https://img/cell.jpg",
                MapCellImageTable.thumbnailUrl to null,
                MapCellImageTable.caption to "남산",
                MapCellImageTable.kind to "LANDMARK",
                MapCellImageTable.copyright to "UNSPECIFIED",
            ),
        ).toCellImageView()

        assertNull(view.thumbnailUrl)
    }

    private fun standard() = CatalogStandardValues(
        mapX = 127.5,
        mapY = 37.5,
        thumbnailImageUrl = "https://img/thumb.jpg",
        category = SpotCategory("RESTAURANT"),
        copyright = CopyrightType.TYPE3,
    )
}
