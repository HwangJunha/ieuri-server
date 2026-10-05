package org.around.ieuri.community.application.qa.required

import org.around.ieuri.community.domain.qa.SpotQuestion
import org.around.ieuri.community.domain.shared.AttachedPhotos

/** 질문 저장소. 해결 표시·숨김은 각자 한 열만, 답변 수는 원자적 증감만 쓴다. */
interface SpotQuestionRepository {
    /** 삭제되지 않은 질문 한 행만 읽는다. 없으면 null. */
    fun findById(questionId: Long): SpotQuestion?

    fun exists(questionId: Long): Boolean

    fun insert(question: SpotQuestion, photos: AttachedPhotos): Long

    /** 해결 표시만 쓴다. 그 사이 삭제됐으면 false. */
    fun changeResolved(questionId: Long, resolved: Boolean): Boolean

    /** 질문과 그 답변·답글, 그 모두의 사진을 물리 삭제한다. */
    fun deleteWithDescendants(questionId: Long)

    /** 신고 누적 숨김. 보이는 질문이 이번 호출로 숨겨졌으면 true. */
    fun hideIfVisible(questionId: Long): Boolean

    // 답변 수: 삭제된 질문은 바꾸지 않고, 0 아래로 내려가지 않는다.
    fun increaseAnswerCount(questionId: Long)

    fun decreaseAnswerCount(questionId: Long)
}
