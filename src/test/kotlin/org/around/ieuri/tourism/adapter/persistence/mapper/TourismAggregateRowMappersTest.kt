package org.around.ieuri.tourism.adapter.persistence.mapper

import org.around.ieuri.tourism.adapter.persistence.table.KtoConcentrationRateTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoSpendingIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoStayIntensityTable
import org.around.ieuri.tourism.adapter.persistence.table.KtoVisitorCountTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailImageTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailInfoTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailIntroTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotDetailTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotFestivalTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.region.ConcentrationRate
import org.around.ieuri.tourism.domain.region.DemandIntensity
import org.around.ieuri.tourism.domain.region.VisitorCount
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.FestivalPeriod
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotFestival
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 애그리게이트 재편으로 생긴 행 변환. 같은 타입 열끼리 뒤바뀌지 않는지 필드마다 다른 값으로 본다. */
@OptIn(InternalApi::class)
class TourismAggregateRowMappersTest {

    private fun row(table: Table) = InsertStatement<Number>(table)

    @Test
    fun `원본 쓰기는 경도를 map_x에, 위도를 map_y에 쓰고 enum은 이름이다`() {
        val row = row(SpotTable)
        Spot(
            contentId = "C-1",
            language = DomainLocale.JA,
            title = "景福宮",
            address = "鍾路",
            thumbnailImageUrl = "https://img/1.jpg",
            coordinates = Coordinates(latitude = 37.58, longitude = 126.97),
            category = SpotCategory.TOURIST_SPOT,
            contentTypeId = 76,
            copyright = CopyrightType.TYPE3,
        ).writeTo(row)

        assertEquals("C-1", row.values[SpotTable.contentId])
        assertEquals("JA", row.values[SpotTable.language])
        assertEquals("景福宮", row.values[SpotTable.title])
        assertEquals("鍾路", row.values[SpotTable.address])
        assertEquals("https://img/1.jpg", row.values[SpotTable.thumbnailImageUrl])
        assertEquals(126.97, row.values[SpotTable.mapX])
        assertEquals(37.58, row.values[SpotTable.mapY])
        assertEquals("TOURIST_SPOT", row.values[SpotTable.category])
        assertEquals(76, row.values[SpotTable.contentTypeId])
        assertEquals("TYPE3", row.values[SpotTable.copyright])
    }

    @Test
    fun `축제 쓰기는 시작일과 종료일을 바꾸지 않는다`() {
        val row = row(SpotFestivalTable)
        SpotFestival(5L, FestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9)), "진행", "문화").writeTo(row)

        assertEquals(5L, row.values[SpotFestivalTable.spotId])
        assertEquals(LocalDate.of(2026, 10, 1), row.values[SpotFestivalTable.eventStartDate])
        assertEquals(LocalDate.of(2026, 10, 9), row.values[SpotFestivalTable.eventEndDate])
        assertEquals("진행", row.values[SpotFestivalTable.progressType])
        assertEquals("문화", row.values[SpotFestivalTable.festivalType])
    }

    @Test
    fun `축제 구분 값이 없으면 null이다`() {
        val row = row(SpotFestivalTable)
        SpotFestival(5L, FestivalPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)), null, null).writeTo(row)

        assertNull(row.values[SpotFestivalTable.progressType])
        assertNull(row.values[SpotFestivalTable.festivalType])
    }

    @Test
    fun `상세 본문 쓰기는 각 필드를 같은 이름의 열에 쓰고 부재 여부를 따로 받는다`() {
        val row = row(SpotDetailTable)
        SpotDetail(
            title = "경복궁",
            category = SpotCategory("CULTURAL_FACILITY"),
            thumbnailImageUrl = "https://img/t.jpg",
            overview = "개요",
            address = "주소",
            homepage = "https://home",
            tel = "02-000",
            copyright = CopyrightType.TYPE1,
        ).writeTo(row, spotId = 7, bodyAbsent = true)

        assertEquals(7L, row.values[SpotDetailTable.spotId])
        assertEquals("경복궁", row.values[SpotDetailTable.title])
        assertEquals("CULTURAL_FACILITY", row.values[SpotDetailTable.category])
        assertEquals("https://img/t.jpg", row.values[SpotDetailTable.thumbnailImageUrl])
        assertEquals("개요", row.values[SpotDetailTable.overview])
        assertEquals("주소", row.values[SpotDetailTable.address])
        assertEquals("https://home", row.values[SpotDetailTable.homepage])
        assertEquals("02-000", row.values[SpotDetailTable.tel])
        assertEquals("TYPE1", row.values[SpotDetailTable.copyright])
        assertEquals(true, row.values[SpotDetailTable.bodyAbsent])
    }

    @Test
    fun `부재 상세는 본문 열이 비어 있다`() {
        val row = row(SpotDetailTable)
        SpotDetail.absent("경복궁", SpotCategory.TOURIST_SPOT, "https://img/t.jpg", null, CopyrightType.UNSPECIFIED).writeTo(row, spotId = 7, bodyAbsent = true)

        assertNull(row.values[SpotDetailTable.overview])
        assertNull(row.values[SpotDetailTable.address])
        assertNull(row.values[SpotDetailTable.homepage])
        assertNull(row.values[SpotDetailTable.tel])
    }

    @Test
    fun `갤러리 쓰기는 순서와 원본·썸네일 URL을 바꾸지 않는다`() {
        val row = row(SpotDetailImageTable)
        SpotImage("https://img/origin.jpg", "https://img/small.jpg", "설명", CopyrightType.TYPE3).writeTo(row, spotId = 7, displayOrder = 2)

        assertEquals(7L, row.values[SpotDetailImageTable.spotId])
        assertEquals(2, row.values[SpotDetailImageTable.displayOrder])
        assertEquals("https://img/origin.jpg", row.values[SpotDetailImageTable.imageUrl])
        assertEquals("https://img/small.jpg", row.values[SpotDetailImageTable.thumbnailUrl])
        assertEquals("설명", row.values[SpotDetailImageTable.caption])
        assertEquals("TYPE3", row.values[SpotDetailImageTable.copyright])
    }

    @Test
    fun `운영 정보 쓰기의 일곱 열이 서로 바뀌지 않는다`() {
        val row = row(SpotDetailIntroTable)
        SpotIntro("09-18", "월", "가능", "02-111", "무료", "불가", "카드").writeTo(row, spotId = 7, category = SpotCategory("RESTAURANT"))

        assertEquals(7L, row.values[SpotDetailIntroTable.spotId])
        assertEquals("RESTAURANT", row.values[SpotDetailIntroTable.category])
        assertEquals("09-18", row.values[SpotDetailIntroTable.businessHours])
        assertEquals("월", row.values[SpotDetailIntroTable.closedDays])
        assertEquals("가능", row.values[SpotDetailIntroTable.parking])
        assertEquals("02-111", row.values[SpotDetailIntroTable.inquiryPhone])
        assertEquals("무료", row.values[SpotDetailIntroTable.fee])
        assertEquals("불가", row.values[SpotDetailIntroTable.petAllowed])
        assertEquals("카드", row.values[SpotDetailIntroTable.creditCardAccepted])
    }

    @Test
    fun `값이 없는 운영 정보는 열이 모두 null인 행으로 쓴다`() {
        val row = row(SpotDetailIntroTable)
        null.writeTo(row, spotId = 7, category = SpotCategory("RESTAURANT"))

        assertEquals(7L, row.values[SpotDetailIntroTable.spotId])
        assertNull(row.values[SpotDetailIntroTable.businessHours])
        assertNull(row.values[SpotDetailIntroTable.creditCardAccepted])
    }

    @Test
    fun `이용안내 쓰기는 이름과 본문, 순서를 바꾸지 않는다`() {
        val row = row(SpotDetailInfoTable)
        SpotInfoItem("입장료", "3,000원").writeTo(row, spotId = 7, category = SpotCategory.TOURIST_SPOT, displayOrder = 4)

        assertEquals(7L, row.values[SpotDetailInfoTable.spotId])
        assertEquals("TOURIST_SPOT", row.values[SpotDetailInfoTable.category])
        assertEquals(4, row.values[SpotDetailInfoTable.displayOrder])
        assertEquals("입장료", row.values[SpotDetailInfoTable.name])
        assertEquals("3,000원", row.values[SpotDetailInfoTable.text])
    }

    @Test
    fun `카탈로그 행은 경도·위도·썸네일·분류·저작권을 바꾸지 않는다`() {
        val standard = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(
                SpotCatalogTable.mapX to 127.25,
                SpotCatalogTable.mapY to 37.75,
                SpotCatalogTable.thumbnailImageUrl to "https://img/c.jpg",
                SpotCatalogTable.category to "SHOPPING",
                SpotCatalogTable.copyright to "TYPE3",
            ),
        ).toCatalogStandardValues()

        assertEquals(127.25, standard.mapX)
        assertEquals(37.75, standard.mapY)
        assertEquals("https://img/c.jpg", standard.thumbnailImageUrl)
        assertEquals("SHOPPING", standard.category.code)
        assertEquals(CopyrightType.TYPE3, standard.copyright)
    }

    @Test
    fun `번역 행은 언어·제목·주소·노출을 옮기고 주소가 없으면 null이다`() {
        val translation = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(
                SpotCatalogTranslationTable.language to "ZH",
                SpotCatalogTranslationTable.title to "景福宮",
                SpotCatalogTranslationTable.address to null,
                SpotCatalogTranslationTable.visible to false,
            ),
        ).toCatalogTranslation()

        assertEquals(DomainLocale.ZH, translation.language)
        assertEquals("景福宮", translation.title)
        assertNull(translation.address)
        assertEquals(false, translation.visible)
    }

    @Test
    fun `원본 축제 기간 행은 원본 id·언어·기간을 옮긴다`() {
        val period = ResultRow.createAndFillValues(
            mapOf<Expression<*>, Any?>(
                SpotTable.id to 21L,
                SpotTable.language to "EN",
                SpotFestivalTable.eventStartDate to LocalDate.of(2026, 10, 1),
                SpotFestivalTable.eventEndDate to LocalDate.of(2026, 10, 9),
            ),
        ).toOriginFestivalPeriod()

        assertEquals(21L, period.spotId)
        assertEquals(DomainLocale.EN, period.language)
        assertEquals(LocalDate.of(2026, 10, 1), period.period.start)
        assertEquals(LocalDate.of(2026, 10, 9), period.period.end)
    }

    @Test
    fun `방문자수 쓰기는 시군구·기준일·구분·수를 바꾸지 않는다`() {
        val row = row(KtoVisitorCountTable)
        VisitorCount("11110", LocalDate.of(2026, 8, 1), "2", BigDecimal("123.5")).writeTo(row)

        assertEquals("11110", row.values[KtoVisitorCountTable.districtCode])
        assertEquals(LocalDate.of(2026, 8, 1), row.values[KtoVisitorCountTable.baseDate])
        assertEquals("2", row.values[KtoVisitorCountTable.visitorType])
        assertEquals(BigDecimal("123.5"), row.values[KtoVisitorCountTable.touristCount])
    }

    @Test
    fun `집중률 쓰기는 관광지명과 대상일을 바꾸지 않는다`() {
        val row = row(KtoConcentrationRateTable)
        ConcentrationRate("26110", "해운대", LocalDate.of(2026, 8, 3), BigDecimal("0.42")).writeTo(row)

        assertEquals("26110", row.values[KtoConcentrationRateTable.districtCode])
        assertEquals("해운대", row.values[KtoConcentrationRateTable.touristSpotName])
        assertEquals(LocalDate.of(2026, 8, 3), row.values[KtoConcentrationRateTable.targetDate])
        assertEquals(BigDecimal("0.42"), row.values[KtoConcentrationRateTable.concentrationRate])
    }

    @Test
    fun `체류 강도 쓰기는 시군구·기준월·지표·값을 바꾸지 않는다`() {
        val row = row(KtoStayIntensityTable)
        DemandIntensity("11110", LocalDate.of(2026, 6, 1), "21", BigDecimal("7.25")).writeStayIntensityTo(row)

        assertEquals("11110", row.values[KtoStayIntensityTable.districtCode])
        assertEquals(LocalDate.of(2026, 6, 1), row.values[KtoStayIntensityTable.baseMonth])
        assertEquals("21", row.values[KtoStayIntensityTable.indicatorCode])
        assertEquals(BigDecimal("7.25"), row.values[KtoStayIntensityTable.indicatorValue])
    }

    @Test
    fun `소비 강도 쓰기는 시군구·기준월·지표·값을 바꾸지 않는다`() {
        val row = row(KtoSpendingIntensityTable)
        DemandIntensity("26110", LocalDate.of(2026, 5, 1), "22", BigDecimal("3.5")).writeSpendingIntensityTo(row)

        assertEquals("26110", row.values[KtoSpendingIntensityTable.districtCode])
        assertEquals(LocalDate.of(2026, 5, 1), row.values[KtoSpendingIntensityTable.baseMonth])
        assertEquals("22", row.values[KtoSpendingIntensityTable.indicatorCode])
        assertEquals(BigDecimal("3.5"), row.values[KtoSpendingIntensityTable.indicatorValue])
    }

    private fun spotRow(address: String?) = ResultRow.createAndFillValues(
        mapOf<Expression<*>, Any?>(
            SpotTable.id to 41L,
            SpotTable.contentId to "C-77",
            SpotTable.language to "JA",
            SpotTable.title to "景福宮",
            SpotTable.address to address,
            SpotTable.thumbnailImageUrl to "https://img/s.jpg",
            SpotTable.mapX to 126.97,
            SpotTable.mapY to 37.58,
            SpotTable.category to "CULTURAL_FACILITY",
            SpotTable.contentTypeId to 78,
            SpotTable.copyright to "TYPE3",
        ),
    )

    @Test
    fun `원본 행은 map_x를 경도로, map_y를 위도로 읽고 나머지 필드를 바꾸지 않는다`() {
        val spot = spotRow(address = "서울 종로구").toSpot()

        assertEquals(41L, spot.id)
        assertEquals("C-77", spot.contentId)
        assertEquals(DomainLocale.JA, spot.language)
        assertEquals("景福宮", spot.title)
        assertEquals("서울 종로구", spot.address)
        assertEquals("https://img/s.jpg", spot.thumbnailImageUrl)
        assertEquals(126.97, spot.coordinates.longitude)
        assertEquals(37.58, spot.coordinates.latitude)
        assertEquals("CULTURAL_FACILITY", spot.category.code)
        assertEquals(78, spot.contentTypeId)
        assertEquals(CopyrightType.TYPE3, spot.copyright)
    }

    @Test
    fun `원본 행의 주소가 없으면 null이다`() {
        assertNull(spotRow(address = null).toSpot().address)
    }
}
