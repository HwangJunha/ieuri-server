package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.confirmVerified
import io.mockk.verify
import org.around.ieuri.community.application.command.CreateSpotQuestionCommand
import org.around.ieuri.community.application.command.DeleteSpotQuestionCommand
import org.around.ieuri.community.application.command.ResolveSpotQuestionCommand
import org.around.ieuri.community.application.exception.InvalidInputException
import org.around.ieuri.community.application.exception.NotQuestionOwnerException
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.domain.vo.ContentLanguage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 질문 작성·삭제·해결(SPEC-0026 R3, R8, R10).
 *
 * **자격 게이팅이 없다** — 로그인 회원이면 그 장소에 어떤 반응을 남겼든(무반응 포함)
 * 질문할 수 있다. 남는 확인은 장소 존재와 입력 검증뿐이다.
 */
class SpotQuestionCommandServiceTest {

    private val memberId = 7L
    private val catalogId = 11L
    private val questionId = 2L
    private val now = Instant.parse("2026-08-18T00:00:00Z")
    private val language = ContentLanguage.EN

    private val questionRepository = mockk<SpotQuestionRepositoryPort>(relaxed = true)
    private val spotLookupPort = mockk<SpotLookupPort>()
    private val photoKeyVerifier = mockk<SpotQaPhotoKeyVerifier>()

    private val service = SpotQuestionCommandService(
        questionRepository, spotLookupPort, photoKeyVerifier,
    )

    private fun givenSpotExists(exists: Boolean = true) {
        every { spotLookupPort.findSummary(catalogId, language) } returns
            if (exists) {
                PostSpotView(
                    id = catalogId, title = "경복궁", thumbnailImageUrl = "https://cdn/x.jpg",
                    category = null, longitude = 127.0, latitude = 37.5,
                )
            } else {
                null
            }
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

    // ── 작성(R3) ───────────────────────────────────────────────────

    @Test
    fun `로그인 회원은 반응과 무관하게 질문을 작성한다`() {
        givenSpotExists()
        givenSaveReturnsWithId()

        assertEquals(questionId, service.createQuestion(command(), memberId, language))
    }

    /**
     * 좋아요 게이팅이 사라졌다는 것을 **포트 부재로** 고정한다 — 서비스가 반응을 물을 방법
     * 자체가 없으므로, 게이팅을 되살리려면 생성자가 바뀌어 이 테스트가 컴파일되지 않는다.
     */
    @Test
    fun `장소 조회 외에는 어떤 외부 조회도 하지 않는다`() {
        givenSpotExists()
        givenSaveReturnsWithId()

        service.createQuestion(command(), memberId, language)

        // 장소에 묻는 것은 존재 검사 하나뿐이다. 반응을 묻는 경로는 여전히 없다.
        verify(exactly = 1) { spotLookupPort.findSummary(catalogId, language) }
        confirmVerified(spotLookupPort)
    }

    @Test
    fun `활성 언어에서 해소되지 않는 장소에는 질문할 수 없다`() {
        givenSpotExists(exists = false)

        assertFailsWith<SpotNotFoundInLocaleException> { service.createQuestion(command(), memberId, language) }
        verify(exactly = 0) { questionRepository.save(any()) }
    }

    // ── 입력 검증(R3, R9) ──────────────────────────────────────────

    @Test
    fun `공백만 있는 본문은 장소를 조회하기 전에 거부된다`() {
        assertFailsWith<InvalidInputException> { service.createQuestion(command(body = "   "), memberId, language) }
        verify(exactly = 0) { spotLookupPort.findSummary(any(), any()) }
    }

    @Test
    fun `501자 본문은 거부된다`() {
        assertFailsWith<InvalidInputException> {
            service.createQuestion(command(body = "가".repeat(501)), memberId, language)
        }
    }

    @Test
    fun `사진 6장은 거부된다`() {
        assertFailsWith<TooManyPhotosException> {
            service.createQuestion(
                command(photoKeys = List(6) { "images/community/$memberId/k$it" }),
                memberId,
                language,
            )
        }
    }

    // ── 해결 표시(R8) ─────────────────────────────────────────────

    @Test
    fun `작성자는 해결 표시를 하고 저장한다`() {
        every { questionRepository.findById(questionId) } returns question()

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = true), memberId)

        verify { questionRepository.save(match { it.resolved }) }
    }

    /** R8 — 되돌리기. 저장되는 애그리거트의 resolved가 실제로 false여야 한다. */
    @Test
    fun `작성자는 해결을 미해결로 되돌리고 저장한다`() {
        every { questionRepository.findById(questionId) } returns question(resolved = true)

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = false), memberId)

        verify { questionRepository.save(match { !it.resolved }) }
    }

    @Test
    fun `이미 해결된 질문에 다시 표시해도 성공이다`() {
        every { questionRepository.findById(questionId) } returns question(resolved = true)

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = true), memberId)

        verify { questionRepository.save(match { it.resolved }) }
    }

    @Test
    fun `이미 미해결인 질문을 되돌려도 성공이다`() {
        every { questionRepository.findById(questionId) } returns question(resolved = false)

        service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = false), memberId)

        verify { questionRepository.save(match { !it.resolved }) }
    }

    @Test
    fun `타인의 질문은 해결 표시할 수 없다`() {
        every { questionRepository.findById(questionId) } returns question(authorId = memberId + 1)

        assertFailsWith<NotQuestionOwnerException> {
            service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = true), memberId)
        }
        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `타인의 질문은 되돌릴 수도 없다`() {
        every { questionRepository.findById(questionId) } returns
            question(authorId = memberId + 1, resolved = true)

        assertFailsWith<NotQuestionOwnerException> {
            service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = false), memberId)
        }
        verify(exactly = 0) { questionRepository.save(any()) }
    }

    @Test
    fun `없는 질문의 해결 표시는 QUESTION_NOT_FOUND다`() {
        every { questionRepository.findById(questionId) } returns null

        assertFailsWith<QuestionNotFoundException> {
            service.resolveQuestion(ResolveSpotQuestionCommand(questionId, resolved = true), memberId)
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
