package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.SpotAnswersSearch
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQuestionsSearch
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotAnswersQuery
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.application.query.SpotQuestionsQuery
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 질문·답변 조회(SPEC-0026 R1, R11, R12, R16).
 *
 * 핵심은 **질문의 숨김이 답변을 함께 가린다**는 종속 방향이다 — 답변 레코드의 hidden을
 * 건드리지 않고 조회 시점에 질문 상태로 판단한다.
 */
class SpotQaQueryServiceTest {

    private val memberId = 7L
    private val catalogId = 11L
    private val questionId = 2L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private val spotQaQueryPort = mockk<SpotQaQueryPort>()
    private val communityQueryPort = mockk<CommunityQueryPort>()
    private val spotLookupPort = mockk<SpotLookupPort>()

    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        uploadUrlTtl = Duration.ofMinutes(30),
        maxUploadBytes = 10_000_000,
        allowedContentTypes = setOf("image/jpeg"),
    )

    private val service = SpotQaQueryService(
        spotQaQueryPort, communityQueryPort, spotLookupPort, policy,
    )

    private fun givenSpotExists(exists: Boolean = true) {
        every { spotLookupPort.findSummary(catalogId) } returns
            if (exists) {
                PostSpotView(
                    id = catalogId, title = "경복궁", thumbnailImageUrl = "https://cdn/x.jpg",
                    category = null, longitude = 127.0, latitude = 37.5,
                )
            } else {
                null
            }
    }

    private fun givenBlockedMembers(ids: Set<Long> = emptySet()) {
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns ids
    }

    private fun questionView(hidden: Boolean = false, ownedByViewer: Boolean = false) =
        SpotQuestionView(
            id = questionId, catalogId = catalogId, body = "질문", authorId = 99L,
            resolved = false, answerCount = 1, ownedByViewer = ownedByViewer,
            hidden = hidden, createdAt = now,
        )

    private fun answerPage() = cursorPageOf(
        edges = listOf(
            CursorEdge(
                cursor = "c1",
                node = SpotAnswerView(
                    id = 9L, questionId = questionId, body = "답변", authorId = 5L,
                    ownedByViewer = false, hidden = false, createdAt = now,
                ),
            ),
        ),
        hasNextPage = false,
        hasPreviousPage = false,
    )

    // ── 질문 목록(R1, R16) ─────────────────────────────────────────

    @Test
    fun `없는 장소는 빈 목록이 아니라 SPOT_NOT_FOUND다`() {
        givenSpotExists(exists = false)

        assertFailsWith<SpotNotFoundException> {
            service.spotQuestions(SpotQuestionsQuery(catalogId, first = 20, after = null), memberId)
        }
    }

    @Test
    fun `first가 상한을 넘으면 절삭된다`() {
        givenSpotExists()
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(SpotQuestionsQuery(catalogId, first = 100, after = null), memberId)

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `차단 집합이 조회 조건으로 넘어간다`() {
        givenSpotExists()
        givenBlockedMembers(setOf(42L))
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(SpotQuestionsQuery(catalogId, first = 20, after = null), memberId)

        assertEquals(setOf(42L), search.captured.viewer.blockedMemberIds)
    }

    @Test
    fun `비로그인은 차단 조회를 하지 않는다`() {
        givenSpotExists()
        every { spotQaQueryPort.findQuestions(any()) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(SpotQuestionsQuery(catalogId, first = 20, after = null), memberId = 0L)

        verify(exactly = 0) { communityQueryPort.findBlockedMemberIds(any()) }
    }

    // ── 답변 목록과 숨김 종속(R11) ─────────────────────────────────

    @Test
    fun `숨겨진 질문의 답변은 함께 가려진다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any()) } returns questionView(hidden = true)

        val page = service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)

        assertTrue(page.edges.isEmpty())
        // 답변을 아예 읽지 않는다 — 가려진 것을 읽어 와 버리는 낭비를 하지 않는다.
        verify(exactly = 0) { spotQaQueryPort.findAnswers(any()) }
    }

    @Test
    fun `숨겨진 질문이라도 작성자 본인에게는 답변이 보인다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any()) } returns
            questionView(hidden = true, ownedByViewer = true)
        every { spotQaQueryPort.findAnswers(any()) } returns answerPage()

        val page = service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)

        assertEquals(1, page.edges.size)
    }

    @Test
    fun `숨겨지지 않은 질문의 답변은 정상 반환된다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any()) } returns questionView()
        every { spotQaQueryPort.findAnswers(any()) } returns answerPage()

        assertEquals(
            1,
            service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId).edges.size,
        )
    }

    @Test
    fun `차단으로 가려진 질문의 답변은 QUESTION_NOT_FOUND다`() {
        givenBlockedMembers(setOf(99L))
        // 차단 필터에 걸려 조회 자체가 비면 질문이 없는 것과 같다 — 우회로를 만들지 않는다.
        every { spotQaQueryPort.findQuestion(questionId, any()) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)
        }
    }

    @Test
    fun `답변 목록의 first도 상한으로 절삭된다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any()) } returns questionView()
        val search = slot<SpotAnswersSearch>()
        every { spotQaQueryPort.findAnswers(capture(search)) } returns answerPage()

        service.spotAnswers(SpotAnswersQuery(questionId, first = 100, after = null), memberId)

        assertEquals(50, search.captured.first)
    }
}
