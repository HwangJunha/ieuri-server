package org.around.ieuri.community.application.service

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.exception.QuestionNotFoundException
import org.around.ieuri.community.application.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.`in`.query.SpotQaQueryUseCase
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.SpotAnswersSearch
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQuestionsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotAnswersQuery
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.application.query.SpotQuestionsQuery
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 장소 질문/답변 조회(SPEC-0026).
 * - 목록·답변·답글은 모두 [viewerContext]를 거친다 — 한 경로라도 빠지면 차단이 무력화된다.
 * - 개인 숨김(hiddenPostIds)은 글 전용이라 읽지 않는다.
 */
@Service
@Transactional(readOnly = true)
class SpotQaQueryService(
    private val spotQaQueryPort: SpotQaQueryPort,
    private val communityQueryPort: CommunityQueryPort,
    private val spotLookupPort: SpotLookupPort,
    private val policy: CommunityPolicy,
) : SpotQaQueryUseCase {

    /**
     * 질문 목록. 장소·작성자(`mine`)·id 필터가 독립적으로 함께 걸린다.
     * - 활성 언어에서 해소되지 않는 장소를 주면 빈 목록이 아니라 SPOT_NOT_FOUND_IN_LOCALE이다.
     * - `mine`은 호출자 자신으로만 좁힌다. 비로그인의 `mine`은 리졸버가 막는다.
     */
    override fun spotQuestions(
        query: SpotQuestionsQuery,
        memberId: Long,
        language: ContentLanguage,
    ): CursorPage<SpotQuestionView> {
        query.catalogId?.let {
            if (spotLookupPort.findSummary(it, language) == null) {
                throw SpotNotFoundInLocaleException()
            }
        }
        return spotQaQueryPort.findQuestions(
            SpotQuestionsSearch(
                catalogId = query.catalogId,
                authorId = if (query.mine) memberId else null,
                ids = query.ids,
                // 활성 언어에서 해소되지 않는 장소의 질문을 뺀다.
                language = language,
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewerContext(memberId),
            ),
        )
    }

    /**
     * 답변 목록. 숨겨진 질문의 답변은 빈 목록이다(작성자 본인 제외) — 답변 레코드는 건드리지 않는다.
     * - 차단으로 걸러진 질문은 QUESTION_NOT_FOUND다.
     */
    override fun spotAnswers(query: SpotAnswersQuery, memberId: Long): CursorPage<SpotAnswerView> {
        val viewer = viewerContext(memberId)
        // 숨김은 우회해 읽는다 — null이면 숨김(빈 목록)과 없음(QUESTION_NOT_FOUND)이 섞인다. 차단은 그대로 걸린다.
        val question = spotQaQueryPort.findQuestion(query.questionId, viewer, includeHidden = true)
            ?: throw QuestionNotFoundException()
        if (question.hidden && !question.ownedByViewer) return emptyPage()
        return spotQaQueryPort.findAnswers(
            SpotAnswersSearch(
                questionId = query.questionId,
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewer,
            ),
        )
    }

    override fun answerReplies(
        answerIds: List<Long>,
        memberId: Long,
    ): Map<Long, List<SpotAnswerReplyView>> = spotQaQueryPort.findRepliesOf(answerIds, viewerContext(memberId))

    override fun questionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findQuestionPhotoKeys(questionIds)

    override fun answerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findAnswerPhotoKeys(answerIds)

    override fun replyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findReplyPhotoKeys(replyIds)

    /** 호출자의 양방향 차단 회원(커뮤니티 글과 같은 집합). 비로그인(memberId = 0)은 조회를 건너뛴다. */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds =
        if (memberId > 0) communityQueryPort.findBlockedMemberIds(memberId) else emptySet(),
    )

    private fun <T> emptyPage(): CursorPage<T> = cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)

    private fun clampPageSize(first: Int): Int = first.coerceIn(1, policy.maxPageSize)
}
