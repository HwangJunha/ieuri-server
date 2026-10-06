package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.shared.SpotCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** 카탈로그 애그리거트(SPEC-0019). 카탈로그는 이름을 갖지 않고 번역에만 둔다. */
class SpotCatalogTest {

    private fun standard(
        mapX: Double = 129.1234,
        mapY: Double = 35.5678,
    ) = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = "https://img/1.jpg",
        category = SpotCategory.TOURIST_SPOT,
        copyright = CopyrightType.TYPE1,
    )

    @Test
    @DisplayName("KO spot에서 만든 카탈로그는 KO 번역만 갖는다")
    fun `korean spot yields a KO translation`() {
        val catalog = SpotCatalog.from(
            language = DomainLocale.KO,
            title = "마린시티",
            address = "부산광역시 해운대구",
            standard = standard(),
        )

        assertEquals(DomainLocale.KO, catalog.language)
        assertEquals("마린시티", catalog.translation.title)
        assertEquals("부산광역시 해운대구", catalog.translation.address)
    }

    @Test
    @DisplayName("외국어 전용 장소는 한국어 번역 없이 존재한다")
    fun `foreign only spot has no korean translation`() {
        // 원본에 KO가 없는 장소에 한국어명을 지어내지 않는다.
        val catalog = SpotCatalog.from(
            language = DomainLocale.EN,
            title = "SM Entertainment",
            address = "Seoul",
            standard = standard(),
        )

        assertEquals(DomainLocale.EN, catalog.language)
        assertEquals("SM Entertainment", catalog.translation.title)
    }

    @Test
    @DisplayName("빈 제목으로는 카탈로그를 만들 수 없다")
    fun `blank title is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpotCatalog.from(DomainLocale.KO, "   ", null, standard())
        }
    }

    @Test
    @DisplayName("번역을 채울 수단이 존재하지 않는다")
    fun `no operation fills a translation`() {
        // AC-6.2. 연산 자체가 없음을 리플렉션으로 고정한다.
        val operations = SpotCatalog::class.java.methods.map { it.name }

        assertFalse(operations.any { it == "fillTranslation" })
        assertFalse(operations.any { it == "needsTranslation" })
    }

    @Test
    @DisplayName("한국 밖 좌표로는 표준값을 만들 수 없다")
    fun `coordinates outside korea are rejected`() {
        // 좌표가 (0,0)이거나 한국 밖인 원본은 깨진 행이다.
        assertThrows(IllegalArgumentException::class.java) { standard(mapX = 0.0, mapY = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { standard(mapX = 139.7, mapY = 35.7) }
    }

    @Test
    @DisplayName("좌표 유효성 판정은 한국 범위를 기준으로 한다")
    fun `coordinate validity follows the korea range`() {
        assertTrue(CatalogStandardValues.isValidCoordinate(129.1234, 35.5678))
        assertFalse(CatalogStandardValues.isValidCoordinate(0.0, 0.0))
        assertFalse(CatalogStandardValues.isValidCoordinate(139.7, 35.7))
    }
}
