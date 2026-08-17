package org.around.ieuri.community.application.port.out.persistence

/**
 * 탈퇴 회원의 커뮤니티 데이터 삭제(SPEC-0011 R17).
 *
 * **글의 자식 행(사진·태그·카테고리·좋아요·숨김·그 글의 댓글)은 지우지 않는다** —
 * `ON DELETE CASCADE`가 DB에서 따라 지운다(JPA cascade가 아니라 물리 제약이다).
 * 남는 것은 회원이 *남의 글에* 남긴 흔적이라 따로 지운다.
 */
interface MemberDataErasePort {
    /** 회원이 쓴 글. 자식 행은 FK cascade가 따라 지운다. 지운 행 수를 돌려준다. */
    fun deletePostsByAuthor(memberId: Long): Long

    /** 회원이 **남의 글에** 단 댓글. 자기 글의 댓글은 글과 함께 사라진다. */
    fun deleteCommentsByAuthor(memberId: Long): Long

    /** 회원이 누른 좋아요. */
    fun deleteLikesByMember(memberId: Long): Long

    /** 회원이 건 숨김. */
    fun deleteHidesByMember(memberId: Long): Long

    /** 회원이 걸었거나 회원을 향한 차단. 양쪽 모두 지운다. */
    fun deleteBlocksInvolving(memberId: Long): Long

    /**
     * 회원이 보낸 DM 메시지(SPEC-0017 R17). **탈퇴만 이 경로를 탄다** — 정지는 해제될 수
     * 있으므로 되돌릴 수 없는 삭제를 하지 않는다.
     *
     * 대화 자체는 지우지 않는다. 상대방 화면에서 남은 자기 메시지가 보여야 하고, 조회가
     * 실패하면 안 된다(AC17-2).
     */
    fun deleteMessagesBySender(memberId: Long): Long

    /**
     * 회원이 쓴 장소 질문(SPEC-0026 R17). 그 질문에 달린 **타인의 답변도 함께** 사라진다
     * (AC-17.2) — 질문 삭제의 종속 규칙과 같으며, FK cascade가 수행한다.
     */
    fun deleteSpotQuestionsByAuthor(memberId: Long): Long

    /** 회원이 **남의 질문에** 단 답변. 자기 질문의 답변은 질문과 함께 사라진다. */
    fun deleteSpotAnswersByAuthor(memberId: Long): Long

    /** 회원이 **남의 답변에** 단 답글. */
    fun deleteSpotAnswerRepliesByAuthor(memberId: Long): Long
}
