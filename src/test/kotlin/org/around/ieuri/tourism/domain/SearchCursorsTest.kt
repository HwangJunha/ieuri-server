package org.around.ieuri.tourism.domain

import org.around.ieuri.tourism.domain.vo.DistanceCursor
import org.around.ieuri.tourism.domain.vo.LikeCountCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** SPEC-0013 커서 인코딩·디코딩(축제·인기). 순수 함수라 도메인 단위로 직접 검증한다. */
class SearchCursorsTest {

    @Test
    fun `거리 커서는 왕복해도 값이 보존된다`() {
        val original = DistanceCursor(hasOrigin = true, distanceMeters = 1234.5678, spotId = 7L)
        assertEquals(original, DistanceCursor.decode(original.encode()))
    }

    @Test
    fun `인기 커서는 왕복해도 값이 보존된다`() {
        val original = LikeCountCursor(hasOrigin = false, likeCount = 99L, spotId = 3L)
        assertEquals(original, LikeCountCursor.decode(original.encode()))
    }

    @Test
    fun `커서는 내용을 그대로 드러내지 않는다`() {
        // 불투명성의 요지는 값을 숨기는 것이 아니라 클라이언트가 조립하지 못하게 막는 것이다.
        val encoded = LikeCountCursor(hasOrigin = true, likeCount = 42L, spotId = 77L).encode()
        assertEquals(false, encoded.contains("42"))
        assertEquals(false, encoded.contains("77"))
    }

    @Test
    fun `다른 종류의 커서는 거부된다`() {
        // 정렬 순서가 다른 커넥션에 커서를 넘기면 조용히 엉뚱한 위치로 뛰는 대신 실패해야 한다.
        val distance = DistanceCursor(hasOrigin = true, distanceMeters = 100.0, spotId = 1L).encode()
        assertFailsWith<IllegalArgumentException> { LikeCountCursor.decode(distance) }
        val like = LikeCountCursor(hasOrigin = true, likeCount = 1L, spotId = 1L).encode()
        assertFailsWith<IllegalArgumentException> { DistanceCursor.decode(like) }
    }

    @Test
    fun `조작된 커서는 거부된다`() {
        assertFailsWith<IllegalArgumentException> { LikeCountCursor.decode("!!!not-base64!!!") }
        assertFailsWith<IllegalArgumentException> { DistanceCursor.decode("") }
    }


    @Test
    fun `원문 보유가 커서에 실린다`() {
        // **정렬 1순위라 커서에 없으면 두 구간의 경계에서 페이지가 어긋난다**(SPEC-0019) —
        // 원문 있는 장소가 소진되고 번역본 구간으로 넘어가는 지점에서 항목이 중복·누락된다.
        val distance = DistanceCursor(hasOrigin = false, distanceMeters = 500.0, spotId = 9L)
        assertEquals(distance, DistanceCursor.decode(distance.encode()))

        val like = LikeCountCursor(hasOrigin = false, likeCount = 7L, spotId = 9L)
        assertEquals(like, LikeCountCursor.decode(like.encode()))
    }

    @Test
    fun `정렬 키가 바뀐 구버전 커서는 거부된다`() {
        // 접두사를 d→d2, l→l2로 올렸다. 구버전 커서를 그대로 받으면 3튜플 파싱이 깨진 채
        // 엉뚱한 위치로 뛴다.
        val oldDistance = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("d:100.0|1".toByteArray())
        assertFailsWith<IllegalArgumentException> { DistanceCursor.decode(oldDistance) }

        val oldLike = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("l:5|1".toByteArray())
        assertFailsWith<IllegalArgumentException> { LikeCountCursor.decode(oldLike) }
    }
}
