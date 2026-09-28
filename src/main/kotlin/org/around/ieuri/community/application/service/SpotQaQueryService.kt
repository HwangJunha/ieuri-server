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
 * 장소 질문/답변 조회(SPEC-0026 R1, R2, R11, R12, R16).
 *
 * 커뮤니티 조회와 같이 [viewerContext]가 핵심이다 — 목록·답변·답글 세 경로가 모두 이것을
 * 거치며, 한 곳만 빠뜨려도 차단이 무력화된다.
 *
 * **개인 숨김(hiddenPostIds)은 읽지 않는다.** 그것은 글 단위 기능이고 질문에는 없다 —
 * 넣으면 쓰이지 않는 쿼리가 조회마다 한 번씩 더 붙는다.
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
     * 질문 목록. **세 필터가 독립적이며 함께 걸린다** — 장소·작성자·id다.
     *
     * 장소를 준 경우에만 존재를 확인한다. 없는 장소는 빈 목록이 아니라 오류이며(AC-1.3),
     * 오타와 "질문 없음"은 구분되어야 한다. 판정 기준은 **활성 언어다**(SPEC-0029 R-16) —
     * 이 언어에서 해소되지 않는 장소도 SPOT_NOT_FOUND_IN_LOCALE로 거절한다(AC-15.3).
     *
     * `mine`은 **호출자 자신으로만 좁힌다** — 회원 id를 인자로 받지 않으므로 남의 목록을
     * 여는 경로가 없다. 비로그인은 좁힐 대상이 없어 리졸버가 미리 막는다.
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
                // 활성 언어에서 해소되지 않는 장소의 질문을 뺀다(R-23). 질문은 장소가 필수라
                // 조건이 무조건 걸린다 — 글과 달리 "장소 없는 항목"이라는 예외가 없다.
                language = language,
                first = clampPageSize(query.first),
                after = query.after,
                viewer = viewerContext(memberId),
            ),
        )
    }

    /**
     * 답변 목록. **질문의 숨김이 답변을 함께 가린다**(R11, AC-11.4).
     *
     * 답변 레코드의 hidden을 건드리지 않고 조회 시점에 질문 상태를 보는 이유는, 질문이
     * 복구되면 답변도 함께 돌아와야 하는데 답변의 상태를 덮어쓰면 원래 숨김이었던 답변과
     * 구분할 수 없게 되기 때문이다.
     *
     * 차단으로 걸러진 질문은 조회 자체가 되지 않아 QUESTION_NOT_FOUND다 — 차단 상대의 질문에
     * 달린 답변을 우회로 읽을 수 없다.
     */
    override fun spotAnswers(query: SpotAnswersQuery, memberId: Long): CursorPage<SpotAnswerView> {
        val viewer = viewerContext(memberId)
        // **숨김 필터를 우회해 읽는다**(includeHidden). 숨겨진 질문을 null로 받으면 "가려졌다"와
        // "없다"가 같은 값이 되어, 아래 종속 판정이 도달 불가능해지고 계약(빈 목록)이 아니라
        // QUESTION_NOT_FOUND가 나간다. 차단은 이 플래그와 무관하게 그대로 걸린다.
        val question = spotQaQueryPort.findQuestion(query.questionId, viewer, includeHidden = true)
            ?: throw QuestionNotFoundException()
        // 숨겨진 질문의 답변은 함께 가려진다(AC-11.4). 작성자 본인에게는 그대로 보인다(AC-11.5).
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

    // 사진 셋. 포트로 그대로 넘긴다 — 여기서 더할 규칙이 없다.
    override fun questionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findQuestionPhotoKeys(questionIds)

    override fun answerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findAnswerPhotoKeys(answerIds)

    override fun replyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>> = spotQaQueryPort.findReplyPhotoKeys(replyIds)

    /**
     * 호출자의 차단 관계를 **양방향으로** 읽어 담는다. 커뮤니티 글과 같은 집합을 쓴다 —
     * 차단은 사람 단위라 어느 표면에서 만나든 같아야 한다.
     *
     * 비로그인(memberId = 0)은 차단 관계가 없어 조회를 건너뛴다.
     */
    private fun viewerContext(memberId: Long): ViewerContext = ViewerContext(
        memberId = memberId,
        blockedMemberIds =
        if (memberId > 0) communityQueryPort.findBlockedMemberIds(memberId) else emptySet(),
    )

    private fun <T> emptyPage(): CursorPage<T> = cursorPageOf(edges = emptyList(), hasNextPage = false, hasPreviousPage = false)

    private fun clampPageSize(first: Int): Int = first.coerceIn(1, policy.maxPageSize)
}
