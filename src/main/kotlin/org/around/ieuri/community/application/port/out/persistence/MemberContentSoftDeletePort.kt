package org.around.ieuri.community.application.port.out.persistence

import java.time.Instant

/**
 * 탈퇴 회원의 공개 콘텐츠 소프트 삭제(SPEC-0011 R17). 본인만의 데이터는 [MemberDataErasePort]가 지운다.
 * - 한 번의 탈퇴는 모든 표에 같은 삭제 시각([now])을 남긴다.
 */
interface MemberContentSoftDeletePort {

    /** 회원이 쓴 글. 이미 지워진 행은 시각을 덮어쓰지 않는다. */
    fun softDeletePostsByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 남의 글에 단 댓글. 자기 글의 댓글은 글이 지워져도 행으로는 남는다. */
    fun softDeleteCommentsByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 쓴 장소 질문(SPEC-0026 R17). 달린 타인의 답변은 칠하지 않는다(부모 질문에서 걸러진다). */
    fun softDeleteSpotQuestionsByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 남의 질문에 단 답변. `answerCount` 감소는 호출자가 한다. */
    fun softDeleteSpotAnswersByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 남의 답변에 단 답글. */
    fun softDeleteSpotAnswerRepliesByAuthor(memberId: Long, now: Instant): Long

    /**
     * 회원이 보낸 DM 메시지(SPEC-0017 R17). 대화 자체는 지우지 않는다.
     * - 보관 기간이 지나면 정리 배치가 이 행도 물리 삭제한다.
     */
    fun softDeleteMessagesBySender(memberId: Long, now: Instant): Long

    /**
     * `answerCount`를 내려야 할 질문 id를 모은다(SPEC-0026 R17).
     * - 소프트 삭제 전에 불러야 한다 — 칠하고 나면 대상을 알 수 없다.
     * - 중복이 그대로 담긴다(그 수만큼 내린다). 숨겨졌거나 이미 지워진 답변은 담기지 않는다.
     */
    fun findQuestionIdsToDecrease(memberId: Long): List<Long>
}
