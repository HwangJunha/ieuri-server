package org.around.ieuri.community.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotAnswersQuery
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.application.query.SpotQuestionsQuery
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 장소 질문/답변 조회(SPEC-0026).
 *
 * **[memberId]가 0이면 비로그인이다** — 목록은 인증 없이도 읽히므로(AC-1.4) 차단·소유 판정만
 * 비어 있는 상태로 흐른다. 커뮤니티 조회가 쓰는 규약과 같다.
 *
 * 사진·작성자·답글은 각각 별도 메서드다. 한 메서드가 전부 조립하면 `{ id body }`만 물어도
 * 조회가 여러 번 붙어 지연 필드로 나눈 의미가 사라진다.
 */
interface SpotQaQueryUseCase {
    /**
     * 정렬은 미해결 우선, 그 안에서 최신순이다(R2).
     *
     * [language]는 요청의 활성 언어이며 장소 필터의 존재 검사 기준이다(SPEC-0029 R-16) —
     * 카탈로그가 없으면 SPOT_NOT_FOUND, 있으나 이 언어에 원본이 없으면
     * SPOT_NOT_FOUND_IN_LOCALE이다(AC-15.3).
     */
    fun spotQuestions(
        query: SpotQuestionsQuery,
        memberId: Long,
        language: ContentLanguage,
    ): CursorPage<SpotQuestionView>

    /**
     * 질문 하나의 답변 목록. 최신순.
     *
     * 질문이 숨겨졌으면(작성자 본인 제외) 답변도 함께 가려진다 — **답변의 hidden을 보지 않고
     * 질문의 상태로 판단한다**(R11). 질문이 없으면 QUESTION_NOT_FOUND.
     */
    fun spotAnswers(query: SpotAnswersQuery, memberId: Long): CursorPage<SpotAnswerView>

    /** 여러 답변의 답글을 **한 번에**. 오래된 순이며 페이지네이션이 없다(AC-7.4). */
    fun answerReplies(answerIds: List<Long>, memberId: Long): Map<Long, List<SpotAnswerReplyView>>

    /** 질문들의 사진 키. 첨부 순서대로이며, 사진이 없는 질문은 결과에 키가 없다. */
    fun questionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>>

    fun answerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>>

    fun replyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>>
}
