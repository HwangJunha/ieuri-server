package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SpotImage 값 객체 — https 불변식·이미지별 저작권 문구 도출. */
class SpotImageTest {

    @Test
    fun `imageUrl은 https여야 한다`() {
        assertFailsWith<IllegalArgumentException> {
            SpotImage(imageUrl = "http://img/a.jpg", thumbnailUrl = null, caption = null, copyright = CopyrightType.TYPE1)
        }
    }

    @Test
    fun `attributionText는 이미지별 copyright에서 도출된다`() {
        val image = SpotImage(
            imageUrl = "https://img/a.jpg",
            thumbnailUrl = "https://img/a_s.jpg",
            caption = "야경",
            copyright = CopyrightType.TYPE3,
        )
        assertEquals(CopyrightType.TYPE3.attributionText, image.attributionText)
    }
}
