package org.around.ieuri.tourism.adapter.graphql.mapper

import org.around.ieuri.tourism.application.shared.provided.dto.CellImageView
import org.around.ieuri.tourism.domain.shared.CellImageKind
import org.around.ieuri.tourism.domain.shared.CopyrightType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 칸 대표 이미지 변환(POL-033). 같은 타입 문자열 두 쌍(`imageUrl`/`thumbnailUrl`, `caption`/`attributionText`)이
 * 뒤바뀌지 않도록 필드마다 다른 값을 넣는다.
 */
class CellImageGqlMapperTest {

    @Test
    fun `모든 필드가 제자리로 옮겨진다`() {
        val view = CellImageView(
            imageUrl = "https://cdn.example/image.jpg",
            thumbnailUrl = "https://cdn.example/thumb.jpg",
            caption = "설악산 대청봉",
            kind = CellImageKind.LANDMARK,
            copyright = CopyrightType.TYPE1,
            attributionText = "한국관광공사",
        )

        val gql = view.toGql()

        assertEquals("https://cdn.example/image.jpg", gql.imageUrl)
        assertEquals("https://cdn.example/thumb.jpg", gql.thumbnailUrl)
        assertEquals("설악산 대청봉", gql.caption)
        assertEquals(CellImageKind.LANDMARK.name, gql.kind.name)
        assertEquals(CopyrightType.TYPE1.name, gql.copyright.name)
        assertEquals("한국관광공사", gql.attributionText)
    }

    /** 유일한 nullable 필드가 빈 경우. 나머지는 도메인에서 non-null이다. */
    @Test
    fun `썸네일이 없어도 변환된다`() {
        val view = CellImageView(
            imageUrl = "https://cdn.example/only.jpg",
            thumbnailUrl = null,
            caption = "본문 없는 칸",
            kind = CellImageKind.LANDMARK,
            copyright = CopyrightType.TYPE1,
            attributionText = "출처",
        )

        val gql = view.toGql()

        assertEquals("https://cdn.example/only.jpg", gql.imageUrl)
        assertNull(gql.thumbnailUrl)
        assertEquals("본문 없는 칸", gql.caption)
        assertEquals("출처", gql.attributionText)
    }
}
