package org.around.ieuri.community.domain

import org.around.ieuri.community.domain.vo.PostTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 사용자 입력 태그의 파싱 규칙.
 *
 * 표기는 유저가 친 그대로 보존하고 매칭은 정규화한 값으로 한다 — "Seoul"로 쓴 글과
 * "seoul"로 쓴 글이 같은 태그 조회에 함께 잡혀야 하기 때문이다.
 */
class PostTagTest {

    @Test
    fun `앞뒤 공백을 제거하고 원문 표기는 보존한다`() {
        val tag = PostTag.parseOrNull("  야간개장  ")

        assertEquals("야간개장", tag?.name)
        assertEquals("야간개장", tag?.normalized)
    }

    @Test
    fun `선행 해시는 제거한다`() {
        // 클라이언트가 #을 붙여 보내도 서버가 벗긴다 — 앱이 정규화할 필요가 없다.
        assertEquals("야경", PostTag.parseOrNull("#야경")?.name)
        assertEquals("야경", PostTag.parseOrNull("  #  야경  ")?.name)
    }

    @Test
    fun `표기는 대소문자를 보존하고 매칭은 소문자로 한다`() {
        val tag = PostTag.parseOrNull("Seoul")

        // 화면에는 유저가 친 대로 보여주되, 조회는 대소문자를 가리지 않아야 한다.
        assertEquals("Seoul", tag?.name)
        assertEquals("seoul", tag?.normalized)
        assertEquals(tag?.normalized, PostTag.parseOrNull("SEOUL")?.normalized)
    }

    @Test
    fun `공백이 섞인 태그는 거부한다`() {
        // "맛집 추천"이 한 태그인지 둘인지 애매해져 검색이 무너진다.
        assertNull(PostTag.parseOrNull("맛집 추천"))
        assertNull(PostTag.parseOrNull("a\tb"))
        assertNull(PostTag.parseOrNull("a\nb"))
    }

    @Test
    fun `빈 값은 거부한다`() {
        assertNull(PostTag.parseOrNull(""))
        assertNull(PostTag.parseOrNull("   "))
        // #만 있는 것도 벗기고 나면 빈 값이다.
        assertNull(PostTag.parseOrNull("#"))
    }

    @Test
    fun `길이 상한은 30자이고 경계값은 허용한다`() {
        val exactly30 = "가".repeat(PostTag.MAX_LENGTH)

        assertEquals(exactly30, PostTag.parseOrNull(exactly30)?.name)
        assertNull(PostTag.parseOrNull("가".repeat(PostTag.MAX_LENGTH + 1)))
    }

    @Test
    fun `길이는 해시와 공백을 제거한 뒤에 잰다`() {
        // 벗기기 전 길이로 재면 "#" 하나 때문에 30자 태그가 거부된다.
        val exactly30 = "가".repeat(PostTag.MAX_LENGTH)

        assertEquals(exactly30, PostTag.parseOrNull("  #$exactly30  ")?.name)
    }

    @Test
    fun `이모지 태그도 코드포인트로 센다`() {
        // 서버가 코드포인트로 세므로 앱의 카운터도 같은 기준이어야 한다(가이드 §8).
        val tag = PostTag.parseOrNull("🥲")

        assertEquals("🥲", tag?.name)
    }
}
