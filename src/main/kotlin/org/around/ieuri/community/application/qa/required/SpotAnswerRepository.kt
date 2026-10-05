package org.around.ieuri.community.application.qa.required

import org.around.ieuri.community.domain.qa.SpotAnswer
import org.around.ieuri.community.domain.shared.AttachedPhotos

interface SpotAnswerRepository {
    /** 삭제되지 않은 답변 한 행만 읽는다. 없으면 null. */
    fun findById(answerId: Long): SpotAnswer?

    fun exists(answerId: Long): Boolean

    fun insert(answer: SpotAnswer, photos: AttachedPhotos): Long

    /**
     * 답변과 그 답글, 사진을 물리 삭제한다.
     * - 이번 호출로 지웠고 그때 숨겨져 있지 않았으면 true — 그때만 질문의 답변 수에 잡혀 있었다. 이미 지워졌으면 false.
     */
    fun deleteWithRepliesWasCounted(answerId: Long): Boolean

    /** 신고 누적 숨김. 보이는 답변이 이번 호출로 숨겨졌으면 그 질문 id, 아니면 null. */
    fun hideIfVisible(answerId: Long): Long?
}
