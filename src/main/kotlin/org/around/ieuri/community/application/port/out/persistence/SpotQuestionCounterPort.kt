package org.around.ieuri.community.application.port.out.persistence

// 탈퇴 정리(MemberDataEraseService)가 쓰는 남은 포트. 탈퇴 정리를 Exposed로 옮길 때 SpotQuestionRepository로 합친다.

/**
 * 질문의 답변 수를 애그리게이트를 거치지 않고 원자적으로 증감한다.
 * - 삭제된 질문은 갱신하지 않으며, 대상이 없어도 오류가 아니다(0건 갱신).
 */
interface SpotQuestionCounterPort {
    fun increaseAnswerCount(questionId: Long)

    /** 0 아래로 내려가지 않는다 — DB의 CHECK 제약과 조건절이 함께 막는다. */
    fun decreaseAnswerCount(questionId: Long)
}
