package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.question.SpotQaQueryService
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswersQuery
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionsQuery
import org.around.ieuri.community.application.question.provided.exception.QuestionNotFoundException
import org.around.ieuri.community.application.question.required.SpotAnswersSearch
import org.around.ieuri.community.application.question.required.SpotQaQueryPort
import org.around.ieuri.community.application.question.required.SpotQuestionsSearch
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.around.ieuri.community.application.shared.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.around.ieuri.community.application.shared.required.SpotLookupPort
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 질문·답변 조회(SPEC-0026 R1, R11, R12, R16).
 * - 질문의 숨김이 조회 시점에 답변을 함께 가린다(답변 레코드는 건드리지 않는다).
 */
class SpotQaQueryServiceTest {

    private val memberId = 7L
    private val catalogId = 11L
    private val questionId = 2L
    private val now = Instant.parse("2026-08-18T00:00:00Z")
    private val language = ContentLanguage.EN

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
        spotQaQueryPort,
        communityQueryPort,
        spotLookupPort,
        policy,
    )

    private fun givenSpotExists(exists: Boolean = true) {
        every { spotLookupPort.findSummary(catalogId, language) } returns
            if (exists) {
                PostSpotView(
                    id = catalogId,
                    title = "경복궁",
                    thumbnailImageUrl = "https://cdn/x.jpg",
                    category = null,
                    longitude = 127.0,
                    latitude = 37.5,
                )
            } else {
                null
            }
    }

    private fun givenBlockedMembers(ids: Set<Long> = emptySet()) {
        every { communityQueryPort.findBlockedMemberIds(memberId) } returns ids
    }

    private fun questionView(hidden: Boolean = false, ownedByViewer: Boolean = false) = SpotQuestionView(
        id = questionId, catalogId = catalogId, body = "질문", authorId = 99L,
        resolved = false, answerCount = 1, ownedByViewer = ownedByViewer,
        hidden = hidden, createdAt = now,
    )

    private fun answerPage() = cursorPageOf(
        edges = listOf(
            CursorEdge(
                cursor = "c1",
                node = SpotAnswerView(
                    id = 9L,
                    questionId = questionId,
                    body = "답변",
                    authorId = 5L,
                    ownedByViewer = false,
                    hidden = false,
                    createdAt = now,
                ),
            ),
        ),
        hasNextPage = false,
        hasPreviousPage = false,
    )

    // ── 질문 목록(R1, R16) ─────────────────────────────────────────

    @Test
    fun `활성 언어에서 해소되지 않는 장소는 빈 목록이 아니라 SPOT_NOT_FOUND_IN_LOCALE이다`() {
        givenSpotExists(exists = false)

        assertFailsWith<SpotNotFoundInLocaleException> {
            service.spotQuestions(
                SpotQuestionsQuery(catalogId = catalogId, first = 20, after = null),
                memberId,
                language,
            )
        }
    }

    /** R-23 — 활성 언어가 조회 조건으로 내려간다. 질문은 장소가 필수라 항상 걸린다. */
    @Test
    fun `활성 언어를 조회 조건으로 내려보낸다`() {
        // 존재 검사도 그 언어로 간다(R-16).
        every { spotLookupPort.findSummary(catalogId, ContentLanguage.JA) } returns
            PostSpotView(
                id = catalogId,
                title = "景福宮",
                thumbnailImageUrl = "https://cdn/x.jpg",
                category = null,
                longitude = 127.0,
                latitude = 37.5,
            )
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, first = 20, after = null),
            memberId,
            ContentLanguage.JA,
        )

        assertEquals(ContentLanguage.JA, search.captured.language)
    }

    @Test
    fun `first가 상한을 넘으면 절삭된다`() {
        givenSpotExists()
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, first = 100, after = null),
            memberId,
            language,
        )

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `차단 집합이 조회 조건으로 넘어간다`() {
        givenSpotExists()
        givenBlockedMembers(setOf(42L))
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, first = 20, after = null),
            memberId,
            language,
        )

        assertEquals(setOf(42L), search.captured.viewer.blockedMemberIds)
    }

    @Test
    fun `비로그인은 차단 조회를 하지 않는다`() {
        givenSpotExists()
        every { spotQaQueryPort.findQuestions(any()) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, first = 20, after = null),
            memberId = 0L,
            language = language,
        )

        verify(exactly = 0) { communityQueryPort.findBlockedMemberIds(any()) }
    }

    // ── 필터 조합(장소·작성자·id) ──────────────────────────────────

    @Test
    fun `mine이면 호출자를 작성자 조건으로 넘긴다`() {
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = null, mine = true, first = 20, after = null),
            memberId,
            language,
        )

        // 작성자 필터는 호출자 자신이다.
        assertEquals(memberId, search.captured.authorId)
        assertNull(search.captured.catalogId)
    }

    @Test
    fun `mine이 아니면 작성자로 좁히지 않는다`() {
        givenSpotExists()
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, first = 20, after = null),
            memberId,
            language,
        )

        assertNull(search.captured.authorId)
    }

    @Test
    fun `장소와 작성자를 함께 좁힐 수 있다`() {
        givenSpotExists()
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = catalogId, mine = true, first = 20, after = null),
            memberId,
            language,
        )

        assertEquals(catalogId, search.captured.catalogId)
        assertEquals(memberId, search.captured.authorId)
    }

    @Test
    fun `ids를 주면 그대로 조회 조건으로 넘어간다`() {
        givenBlockedMembers()
        val search = slot<SpotQuestionsSearch>()
        every { spotQaQueryPort.findQuestions(capture(search)) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = null, ids = listOf(5L), first = 20, after = null),
            memberId,
            language,
        )

        assertEquals(listOf(5L), search.captured.ids)
    }

    /** 장소를 주지 않았으면 존재 확인을 하지 않는다. */
    @Test
    fun `장소를 주지 않으면 장소 존재를 묻지 않는다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestions(any()) } returns
            cursorPageOf(emptyList(), hasNextPage = false, hasPreviousPage = false)

        service.spotQuestions(
            SpotQuestionsQuery(catalogId = null, mine = true, first = 20, after = null),
            memberId,
            language,
        )

        verify(exactly = 0) { spotLookupPort.findSummary(any(), any()) }
    }

    // ── 답변 목록과 숨김 종속(R11) ─────────────────────────────────

    @Test
    fun `숨겨진 질문의 답변은 함께 가려진다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any(), includeHidden = true) } returns questionView(hidden = true)

        val page = service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)

        assertTrue(page.edges.isEmpty())
        // 답변을 아예 읽지 않는다.
        verify(exactly = 0) { spotQaQueryPort.findAnswers(any()) }
    }

    @Test
    fun `숨겨진 질문이라도 작성자 본인에게는 답변이 보인다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any(), includeHidden = true) } returns
            questionView(hidden = true, ownedByViewer = true)
        every { spotQaQueryPort.findAnswers(any()) } returns answerPage()

        val page = service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)

        assertEquals(1, page.edges.size)
    }

    @Test
    fun `숨겨지지 않은 질문의 답변은 정상 반환된다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any(), includeHidden = true) } returns questionView()
        every { spotQaQueryPort.findAnswers(any()) } returns answerPage()

        assertEquals(
            1,
            service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId).edges.size,
        )
    }

    @Test
    fun `차단으로 가려진 질문의 답변은 QUESTION_NOT_FOUND다`() {
        givenBlockedMembers(setOf(99L))
        // includeHidden은 숨김만 우회한다 — 차단은 그대로 걸려 null이 된다.
        every { spotQaQueryPort.findQuestion(questionId, any(), includeHidden = true) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.spotAnswers(SpotAnswersQuery(questionId, first = 20, after = null), memberId)
        }
    }

    @Test
    fun `답변 목록의 first도 상한으로 절삭된다`() {
        givenBlockedMembers()
        every { spotQaQueryPort.findQuestion(questionId, any(), includeHidden = true) } returns questionView()
        val search = slot<SpotAnswersSearch>()
        every { spotQaQueryPort.findAnswers(capture(search)) } returns answerPage()

        service.spotAnswers(SpotAnswersQuery(questionId, first = 100, after = null), memberId)

        assertEquals(50, search.captured.first)
    }
}
