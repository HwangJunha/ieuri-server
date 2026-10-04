package org.around.ieuri.community.application.question.provided

import org.around.ieuri.community.application.question.provided.dto.CreateSpotQuestionCommand
import org.around.ieuri.community.domain.vo.ContentLanguage

/** 장소 질문 쓰기. 장소 반응 여부로 막지 않는다. */
interface SpotQuestionCommandUseCase {
    /** 생성된 질문 id. 장소는 [language]에서 해소되는 것만 쓸 수 있다(아니면 SPOT_NOT_FOUND_IN_LOCALE). */
    fun createQuestion(command: CreateSpotQuestionCommand, memberId: Long, language: ContentLanguage): Long

    /** 딸린 답변·답글(타인의 것 포함)도 함께 삭제된다. */
    fun deleteQuestion(questionId: Long, memberId: Long)

    /** 해결·미해결 양방향이며 양쪽 다 멱등이다. 답변은 영향받지 않는다. */
    fun resolveQuestion(questionId: Long, resolved: Boolean, memberId: Long)
}
