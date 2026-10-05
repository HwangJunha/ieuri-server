package org.around.ieuri.community.domain.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PostTagTest {

    @Test
    fun `선행 #과 앞뒤 공백을 떼고 이름은 그대로, 매칭 키는 소문자다`() {
        val tag = PostTag.parse("  #Seoul ")

        assertEquals("Seoul", tag.name)
        assertEquals("seoul", tag.normalized)
    }

    @Test
    fun `비었거나 길거나 공백이 섞이면 조회용 파싱은 null이고 글용 파싱은 오류다`() {
        listOf("", "#", "a b", "가".repeat(PostTag.MAX_LENGTH + 1)).forEach { raw ->
            assertNull(PostTag.parseOrNull(raw), raw)
            assertFailsWith<InvalidTagException>(raw) { PostTag.parse(raw) }
        }
    }

    @Test
    fun `글 하나의 태그는 5개까지다`() {
        val five = (1..PostTag.MAX_PER_POST).map { PostTag.parse("t$it") }

        assertEquals(five, PostTags.of(five).values)
        assertFailsWith<TooManyTagsException> { PostTags.of(five + PostTag.parse("t6")) }
    }
}
