package org.around.ieuri.community.adapter.graphql.mapper

import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.conversation.provided.dto.ConversationSpotQaOriginView
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommunityResponseMappersTest {

    @Test
    fun `작성자가 있으면 id·닉네임·프로필을 그대로 옮긴다`() {
        val author = MemberSummaryView(id = 1L, nickname = "여행자", profileImageUrl = "https://cdn/p.png").toGqlOrPlaceholder(memberId = 2L)

        assertEquals(1L, author.id)
        assertEquals("여행자", author.nickname)
        assertEquals("https://cdn/p.png", author.profileImageUrl)
    }

    @Test
    fun `작성자 조회가 비면 원래 작성자 id에 대체 닉네임을 두고 프로필은 null이다`() {
        val author = (null as MemberSummaryView?).toGqlOrPlaceholder(memberId = 2L)

        assertEquals(2L, author.id)
        assertEquals(WITHDRAWN_NICKNAME, author.nickname)
        assertNull(author.profileImageUrl)
    }

    @Test
    fun `페이지 정보는 앞뒤 여부와 시작·끝 커서를 바꾸지 않는다`() {
        val pageInfo = CursorPageInfo(hasNextPage = true, hasPreviousPage = false, startCursor = "start", endCursor = "end").toGql()

        assertEquals(true, pageInfo.hasNextPage)
        assertEquals(false, pageInfo.hasPreviousPage)
        assertEquals("start", pageInfo.startCursor)
        assertEquals("end", pageInfo.endCursor)
    }

    @Test
    fun `커서가 없는 빈 페이지는 두 커서가 null이다`() {
        val pageInfo = CursorPageInfo(hasNextPage = false, hasPreviousPage = false, startCursor = null, endCursor = null).toGql()

        assertNull(pageInfo.startCursor)
        assertNull(pageInfo.endCursor)
    }

    @Test
    fun `DM 출처는 장소·질문·답변의 id와 스냅샷을 서로 바꾸지 않는다`() {
        val origin = ConversationSpotQaOriginView(
            catalogId = 1L,
            spotTitle = "경복궁",
            questionId = 2L,
            questionBodySnapshot = "질문 원문",
            answerId = 3L,
            answerBodySnapshot = "답변 원문",
        ).toGql()

        assertEquals(1L, origin.catalogId)
        assertEquals("경복궁", origin.spotTitle)
        assertEquals(2L, origin.questionId)
        assertEquals("질문 원문", origin.questionBodySnapshot)
        assertEquals(3L, origin.answerId)
        assertEquals("답변 원문", origin.answerBodySnapshot)
    }
}
