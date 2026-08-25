package org.around.ieuri.community.application.port.out.persistence

import java.time.Instant

/**
 * 탈퇴 회원 콘텐츠의 소프트 삭제(SPEC-0011 R17).
 *
 * **물리 삭제와 나눈 이유는 남의 화면 때문이다.** 글·댓글·질문·답변·답글은 다른 회원에게도
 * 보이는 콘텐츠라, 지우면 남의 스레드에 구멍이 나고 답변이 달린 질문이 통째로 사라진다.
 * 행은 남기고 표시에서만 지운다 — 읽기 계층이 이미 `deletedAt IS NULL`로 거른다.
 *
 * 본인만의 데이터(좋아요·숨김·차단 등)는 남길 이유가 없어 [MemberDataErasePort]가 그대로
 * 물리 삭제한다.
 *
 * **삭제 시각을 인자로 받는다** — 한 번의 탈퇴가 남기는 시각이 표마다 갈리면 나중에
 * "이 회원이 언제 탈퇴했는가"를 한 값으로 읽을 수 없다.
 */
interface MemberContentSoftDeletePort {

    /** 회원이 쓴 글. 이미 지워진 행은 시각을 덮어쓰지 않는다. */
    fun softDeletePostsByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 **남의 글에** 단 댓글. 자기 글의 댓글은 글이 지워져도 행으로는 남는다. */
    fun softDeleteCommentsByAuthor(memberId: Long, now: Instant): Long

    /**
     * 회원이 쓴 장소 질문(SPEC-0026 R17).
     *
     * **타인의 답변을 따로 칠하지 않아도 AC-17.2가 지켜진다** — 답변 조회가 부모 질문을 먼저
     * 읽어 거르므로 질문이 지워지면 그 답변에 도달할 수 없다. 자식을 훑는 방식은 자식 수에
     * 상한이 없어 기각됐다.
     */
    fun softDeleteSpotQuestionsByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 **남의 질문에** 단 답변. `answerCount` 감소는 호출자가 한다. */
    fun softDeleteSpotAnswersByAuthor(memberId: Long, now: Instant): Long

    /** 회원이 **남의 답변에** 단 답글. */
    fun softDeleteSpotAnswerRepliesByAuthor(memberId: Long, now: Instant): Long
}
