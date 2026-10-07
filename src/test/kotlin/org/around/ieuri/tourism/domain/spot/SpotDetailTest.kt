package org.around.ieuri.tourism.domain.spot

import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SpotDetail 도메인 모델 — 불변식(https·10장 캡·likeCount)·저작권 도출·withLikeCount(§4·§5). */
class SpotDetailTest {

    private fun detail(
        copyright: CopyrightType = CopyrightType.TYPE1,
    ) = SpotDetail(
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = "조선의 법궁",
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = copyright,
    )

    @Test
    fun `attributionText는 copyright에서 도출된다`() {
        assertEquals(CopyrightType.TYPE3.attributionText, detail(copyright = CopyrightType.TYPE3).attributionText)
    }

    @Test
    fun `썸네일이 https가 아니면 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            detail().copy(thumbnailImageUrl = "http://img/100.jpg")
        }
    }

    @Test
    fun `title이 비면 거부한다`() {
        assertFailsWith<IllegalArgumentException> { detail().copy(title = " ") }
    }

    @Test
    fun `overview·homepage·tel은 null일 수 있다(부분 데이터)`() {
        val d = detail().copy(overview = null, homepage = null, tel = null, address = null)
        assertTrue(d.overview == null && d.homepage == null && d.tel == null && d.address == null)
    }
}
