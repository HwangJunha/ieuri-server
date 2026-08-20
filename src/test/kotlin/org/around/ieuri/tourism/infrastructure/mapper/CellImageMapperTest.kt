package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.vo.CellImageKind
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.CellImageRow
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toCellImageView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 저장 행 → 칸 이미지 읽기 모델(POL-033).
 *
 * **필드마다 값을 다르게 준다** — 같은 값을 쓰면 두 문자열 필드가 뒤바뀌어도 통과한다.
 */
class CellImageMapperTest {

    @Test
    fun `모든 필드가 제자리로 간다`() {
        val row = CellImageRow(
            cellCode = "11",
            imageUrl = "https://img.example/original.jpg",
            thumbnailUrl = "https://img.example/thumb.jpg",
            caption = "경복궁",
            kind = "LANDMARK",
            copyright = "TYPE1",
        )

        val view = row.toCellImageView()

        assertEquals("https://img.example/original.jpg", view.imageUrl)
        assertEquals("https://img.example/thumb.jpg", view.thumbnailUrl)
        assertEquals("경복궁", view.caption)
        assertEquals(CellImageKind.LANDMARK, view.kind)
        assertEquals(CopyrightType.TYPE1, view.copyright)
    }

    @Test
    fun `attributionText는 저장 값이 아니라 저작권 유형에서 파생된다`() {
        val type3 = CellImageRow("11", "u", null, "c", "SPECIALTY", "TYPE3").toCellImageView()

        assertEquals(CopyrightType.TYPE3.attributionText, type3.attributionText)
        assertEquals(CellImageKind.SPECIALTY, type3.kind)
    }

    @Test
    fun `UNSPECIFIED도 출처 표기가 채워진다`() {
        val view = CellImageRow("11", "u", null, "c", "LANDMARK", "UNSPECIFIED").toCellImageView()

        assertEquals(CopyrightType.UNSPECIFIED, view.copyright)
        assertEquals(CopyrightType.UNSPECIFIED.attributionText, view.attributionText)
    }

    @Test
    fun `nullable인 썸네일이 비어도 변환된다`() {
        val view = CellImageRow("11", "https://img/only.jpg", null, "이름", "LANDMARK", "TYPE1").toCellImageView()

        assertNull(view.thumbnailUrl)
        assertEquals("https://img/only.jpg", view.imageUrl)
    }
}
