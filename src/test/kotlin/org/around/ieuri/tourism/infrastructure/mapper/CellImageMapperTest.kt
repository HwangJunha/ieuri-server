package org.around.ieuri.tourism.infrastructure.mapper

import org.around.ieuri.tourism.domain.vo.CellImageKind
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellImageEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toCellImageView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 엔티티 → 칸 이미지 읽기 모델(POL-033). 필드마다 값을 다르게 준다. */
class CellImageMapperTest {

    @Test
    fun `모든 필드가 제자리로 간다`() {
        val view = entity(
            imageUrl = "https://img.example/original.jpg",
            thumbnailUrl = "https://img.example/thumb.jpg",
            caption = "경복궁",
            kind = CellImageKind.LANDMARK,
            copyright = CopyrightType.TYPE1,
        ).toCellImageView()

        assertEquals("https://img.example/original.jpg", view.imageUrl)
        assertEquals("https://img.example/thumb.jpg", view.thumbnailUrl)
        assertEquals("경복궁", view.caption)
        assertEquals(CellImageKind.LANDMARK, view.kind)
        assertEquals(CopyrightType.TYPE1, view.copyright)
    }

    @Test
    fun `attributionText는 저장 값이 아니라 저작권 유형에서 파생된다`() {
        val view = entity(kind = CellImageKind.SPECIALTY, copyright = CopyrightType.TYPE3)
            .toCellImageView()

        assertEquals(CopyrightType.TYPE3.attributionText, view.attributionText)
        assertEquals(CellImageKind.SPECIALTY, view.kind)
    }

    @Test
    fun `UNSPECIFIED도 출처 표기가 채워진다`() {
        val view = entity(copyright = CopyrightType.UNSPECIFIED).toCellImageView()

        assertEquals(CopyrightType.UNSPECIFIED, view.copyright)
        assertEquals(CopyrightType.UNSPECIFIED.attributionText, view.attributionText)
    }

    @Test
    fun `nullable인 썸네일이 비어도 변환된다`() {
        val view = entity(imageUrl = "https://img/only.jpg", thumbnailUrl = null).toCellImageView()

        assertNull(view.thumbnailUrl)
        assertEquals("https://img/only.jpg", view.imageUrl)
    }

    private fun entity(
        imageUrl: String = "https://img/o.jpg",
        thumbnailUrl: String? = "https://img/t.jpg",
        caption: String = "이름",
        kind: CellImageKind = CellImageKind.LANDMARK,
        copyright: CopyrightType = CopyrightType.TYPE1,
    ) = MapCellImageEntity(
        cellId = 11L,
        displayOrder = 0,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        kind = kind,
        copyright = copyright,
    )
}
