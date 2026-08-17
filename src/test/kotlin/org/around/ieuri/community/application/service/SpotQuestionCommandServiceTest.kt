package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotEligibleToAskException
import org.around.ieuri.community.application.exception.NotQuestionOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.out.client.SpotLikeQueryPort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 질문 작성·삭제·해결(SPEC-0026 R3, R5, R8, R10).
 *
 * 핵심은 **작성 자격이 쓰기 경로에서만 판정된다**는 것이다 — 반응 없음과 DISLIKE가 똑같이
 * 거부되고, 판정은 커맨드를 처리하는 순간에 일어난다.
 */
class SpotQuestionCommandServiceTest {

    private val memberId = 7L
    private val catalogId = 11L
    private val questionId = 2L
    private val now = Instant.parse("2026-08-18T00:00:00Z")

    private val questionRepository = mockk<SpotQuestionRepositoryPort>(relaxed = true)
    private val spotLikeQueryPort = mockk<SpotLikeQueryPort>()
    private val spotLookupPort = mockk<SpotLookupPort>()
    private val photoKeyVerifier = mockk<SpotQaPhotoKeyVerifier>()

    private val service = SpotQuestionCommandService(
        questionRepository, spotLikeQueryPort, spotLookupPort, photoKeyVerifier,
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

    private fun givenEligible(eligible: Boolean = true) {
        every { spotLikeQueryPort.likedByMember(catalogId, memberId) } returns eligible
    }

    private fun givenSaveReturnsWithId() {
        every { questionRepository.save(any()) } answers {
            val q = firstArg<SpotQuestion>()
            SpotQuestion.restore(
                id = questionId, catalogId = q.catalogId, authorId = q.authorId, body = q.body,
                photoKeys = q.photoKeys, resolved = q.resolved, answerCount = q.answerCount,
                hidden = q.hidden, deletedAt = q.deletedAt, createdAt = now,
            )
        }
    }

    private fun command(body: String = "웨이팅 있나요?", photoKeys: List<String> = emptyList()) =
        CreateSpotQuestionCommand(catalogId = catalogId, body = body, photoKeys = photoKeys)

    private fun question(authorId: Long = memberId, resolved: Boolean = false) =
        SpotQuestion.restore(
            id = questionId, catalogId = catalogId, authorId = authorId, body = "질문",
            photoKeys = emptyList(), resolved = resolved, answerCount = 0,
            hidden = false, deletedAt = null, createdAt = now,
        )

    // ── 작성 자격(R3, R5) ───────────────────────────────────────────

    @Test
    fun `좋아요한 회원은 질문을 작성한다`() {
        givenSpotExists()
        givenEligible()
        givenSaveReturnsWithId()

        assertEquals(questionId, service.createQuestion(command(), memberId))
    }

    @Test
    fun `반응이 없으면 작성 자격이 없다`() {
        givenSpotExists()
        givenEligible(eligible = false)

        assertFailsWith<NotEligibleToAskException> { service.createQuestion(command(), memberId) }
        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `자격 판정은 커맨드 처리 시점에 이루어진다`() {
        givenSpotExists()
        givenEligible()
        givenSaveReturnsWithId()

        service.createQuestion(command(), memberId)

        // 화면 진입이 아니라 이 순간에 물었다는 것을 고정한다(AC-5.4).
        verify(exactly = 1) { spotLikeQueryPort.likedByMember(catalogId, memberId) }
    }

    @Test
    fun `없는 장소는 자격을 묻기 전에 거부된다`() {
        givenSpotExists(exists = false)

        assertFailsWith<SpotNotFoundException> { service.createQuestion(command(), memberId) }
        verify(exactly = 0) { spotLikeQueryPort.likedByMember(any(), any()) }
    }

    // ── 입력 검증(R3, R9) ──────────────────────────────────────────

    @Test
    fun `공백만 있는 본문은 자격을 묻기 전에 거부된다`() {
        assertFailsWith<InvalidInputException> { service.createQuestion(command(body = "   "), memberId) }
        verify(exactly = 0) { spotLookupPort.findSummary(any()) }
    }

    @Test
    fun `501자 본문은 거부된다`() {
        assertFailsWith<InvalidInputException> {
            service.createQuestion(command(body = "가".repeat(501)), memberId)
        }
    }

    @Test
    fun `사진 6장은 거부된다`() {
        assertFailsWith<TooManyPhotosException> {
            service.createQuestion(command(photoKeys = List(6) { "images/community/$memberId/k$it" }), memberId)
        }
    }

    // ── 해결 표시(R8) ─────────────────────────────────────────────

    @Test
    fun `작성자는 해결 표시를 하고 저장한다`() {
        every { questionRepository.findById(questionId) } returns question()

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId), memberId)

        verify { questionRepository.save(match { it.resolved }) }
    }

    @Test
    fun `이미 해결된 질문에 다시 표시해도 성공이다`() {
        every { questionRepository.findById(questionId) } returns question(resolved = true)

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId), memberId)

        verify { questionRepository.save(match { it.resolved }) }
    }

    @Test
    fun `타인의 질문은 해결 표시할 수 없다`() {
        every { questionRepository.findById(questionId) } returns question(authorId = memberId + 1)

        assertFailsWith<NotQuestionOwnerException> {
            service.resolveQuestion(ResolveSpotQuestionCommand(questionId), memberId)
        }
        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `없는 질문의 해결 표시는 QUESTION_NOT_FOUND다`() {
        every { questionRepository.findById(questionId) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.resolveQuestion(ResolveSpotQuestionCommand(questionId), memberId)
        }
    }

    // ── 삭제(R10) ─────────────────────────────────────────────────

    @Test
    fun `작성자는 질문과 그 자손을 함께 지운다`() {
        every { questionRepository.findById(questionId) } returns question()

        service.deleteQuestion(DeleteSpotQuestionCommand(questionId), memberId)

        verify { questionRepository.deleteWithDescendants(questionId) }
    }

    @Test
    fun `타인의 질문은 삭제할 수 없다`() {
        every { questionRepository.findById(questionId) } returns question(authorId = memberId + 1)

        assertFailsWith<NotQuestionOwnerException> {
            service.deleteQuestion(DeleteSpotQuestionCommand(questionId), memberId)
        }
        verify(exactly = 0) { questionRepository.deleteWithDescendants(any()) }
    }

    @Test
    fun `없는 질문의 삭제는 QUESTION_NOT_FOUND다`() {
        every { questionRepository.findById(questionId) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.deleteQuestion(DeleteSpotQuestionCommand(questionId), memberId)
        }
    }
}
