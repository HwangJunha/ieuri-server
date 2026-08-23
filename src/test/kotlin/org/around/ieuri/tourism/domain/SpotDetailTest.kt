package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SpotDetail 도메인 모델 — 불변식(https·10장 캡·likeCount)·저작권 도출·withLikeCount(§4·§5). */
class SpotDetailTest {

    private fun detail(
        copyright: CopyrightType = CopyrightType.TYPE1,
        likeCount: Long = 0,
    ) = SpotDetail(
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = "조선의 법궁",
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = copyright,
        likeCount = likeCount,
    )

    @Test
    fun `attributionText는 copyright에서 도출된다`() {
        assertEquals(CopyrightType.TYPE3.attributionText, detail(copyright = CopyrightType.TYPE3).attributionText)
    }

    @Test
    fun `withLikeCount는 likeCount만 얹은 복제본을 반환한다`() {
        val base = detail(likeCount = 0)
        val withLikes = base.withLikeCount(7)
        assertEquals(7, withLikes.likeCount)
        assertEquals(0, base.likeCount) // 원본 불변
    }

    @Test
    fun `썸네일이 https가 아니면 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            detail().copy(thumbnailImageUrl = "http://img/100.jpg")
        }
    }

    @Test
    fun `likeCount는 음수일 수 없다`() {
        assertFailsWith<IllegalArgumentException> { detail(likeCount = -1) }
    }

    @Test
    fun `title이 비면 거부한다`() {
        // id 불변식은 사라졌다(V20) — 이 모델은 식별자를 담지 않고 읽기 DTO가 소유한다.
        assertFailsWith<IllegalArgumentException> { detail().copy(title = " ") }
    }

    @Test
    fun `overview·homepage·tel은 null일 수 있다(부분 데이터)`() {
        val d = detail().copy(overview = null, homepage = null, tel = null, address = null)
        assertTrue(d.overview == null && d.homepage == null && d.tel == null && d.address == null)
    }
}
