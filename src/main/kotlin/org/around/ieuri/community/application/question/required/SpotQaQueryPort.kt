package org.around.ieuri.community.application.question.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerOriginView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.question.required.dto.SpotAnswersSearch
import org.around.ieuri.community.application.question.required.dto.SpotQuestionsSearch
import org.around.ieuri.community.application.shared.required.dto.ViewerContext

/**
 * 장소 질문/답변 읽기 아웃바운드 포트(SPEC-0026). 애그리게이트를 우회해 필요한 값만 프로젝션한다.
 */
interface SpotQaQueryPort {
    /**
     * 질문 목록. 정렬은 `resolved ASC, createdAt DESC, id DESC`이며 인자로 바꿀 수 없다.
     * - 커서는 정렬 키 전체를 담는다.
     */
    fun findQuestions(search: SpotQuestionsSearch): CursorPage<SpotQuestionView>

    /**
     * 질문 단건. 차단 필터는 항상 걸리며, 걸러지면 null이다.
     * - [includeHidden]이 true면 숨김 필터만 우회한다 — 호출자가 `hidden`으로 판단한다.
     * - 차단은 이 플래그와 무관하게 우회하지 않는다.
     */
    fun findQuestion(
        questionId: Long,
        viewer: ViewerContext,
        includeHidden: Boolean = false,
    ): SpotQuestionView?

    /** 답변 목록. 최신순. 질문의 숨김 종속(R11)은 호출자가 이미 판정한 뒤 부른다. */
    fun findAnswers(search: SpotAnswersSearch): CursorPage<SpotAnswerView>

    /**
     * 여러 답변의 답글을 한 번에. 오래된 순이며 페이지네이션이 없다.
     * 답글이 없는 답변은 결과에 키가 없다.
     */
    fun findRepliesOf(
        answerIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<SpotAnswerReplyView>>

    /** 여러 질문의 사진 키를 한 번에(DataLoader용). 사진이 없으면 결과에 키가 없다. */
    fun findQuestionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>>

    fun findAnswerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>>

    fun findReplyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>>

    /** 지워지지 않은 질문·답변·답글이 있는가. 모더레이션 필터를 거치지 않는다. */
    fun questionExists(questionId: Long): Boolean

    fun answerExists(answerId: Long): Boolean

    fun replyExists(replyId: Long): Boolean

    /** 답변과 질문의 원문. 둘 중 하나라도 지워졌으면 null이다. */
    fun findAnswerOrigin(answerId: Long): SpotAnswerOriginView?
}
