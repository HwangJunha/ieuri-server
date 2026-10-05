package org.around.ieuri.community.application.question.provided

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswersQuery
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionsQuery
import org.around.ieuri.community.domain.shared.ContentLanguage

/**
 * 장소 질문/답변 조회(SPEC-0026). 사진·답글은 지연 필드용 별도 메서드다.
 * - memberId가 0이면 비로그인이다 — 차단·소유 판정이 빈 상태로 흐른다.
 */
interface SpotQaQueryUseCase {
    /**
     * 질문 목록. 미해결 우선, 그 안에서 최신순이다.
     * - 장소 필터는 [language] 기준으로 검사한다: 카탈로그가 없으면 SPOT_NOT_FOUND,
     *   이 언어에 원본이 없으면 SPOT_NOT_FOUND_IN_LOCALE(SPEC-0029).
     */
    fun spotQuestions(
        query: SpotQuestionsQuery,
        memberId: Long,
        language: ContentLanguage,
    ): CursorPage<SpotQuestionView>

    /**
     * 질문 하나의 답변 목록. 최신순. 질문이 없으면 QUESTION_NOT_FOUND.
     * - 질문이 숨겨졌으면(작성자 본인 제외) 답변도 함께 가려진다(R11).
     */
    fun spotAnswers(query: SpotAnswersQuery, memberId: Long): CursorPage<SpotAnswerView>

    /** 여러 답변의 답글을 한 번에. 오래된 순이며 페이지네이션이 없다. */
    fun answerReplies(answerIds: List<Long>, memberId: Long): Map<Long, List<SpotAnswerReplyView>>

    /** 질문들의 사진 키. 첨부 순서대로이며, 사진이 없는 질문은 결과에 키가 없다. */
    fun questionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>>

    fun answerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>>

    fun replyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>>
}
