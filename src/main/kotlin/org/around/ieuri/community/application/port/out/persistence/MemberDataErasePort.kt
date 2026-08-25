package org.around.ieuri.community.application.port.out.persistence

/**
 * 탈퇴 회원의 커뮤니티 데이터 **물리 삭제**(SPEC-0011 R17).
 *
 * **여기 남은 것은 본인만의 데이터다** — 좋아요·숨김·차단·DM. 남의 화면에 보이는 콘텐츠
 * (글·댓글·질문·답변·답글)는 지우면 남의 스레드가 깨지므로 소프트 삭제로 옮겼다
 * ([MemberContentSoftDeletePort]).
 */
interface MemberDataErasePort {
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
     * `answerCount`를 내려야 할 질문 id를 모은다(SPEC-0026 R17).
     *
     * **삭제하지 않는다** — 답변 자체는 소프트 삭제로 처리되고, 이 메서드는 세는 일만 한다.
     * 수를 내리지 않으면 답변 3개라고 표시된 질문에 2개만 보여 유저가 세어 보고 버그라고
     * 판단한다.
     *
     * **중복이 그대로 담긴다** — 같은 질문에 이 회원의 답변이 여러 건이면 그 수만큼 내려야
     * 한다. 숨겨진 답변은 이미 수에서 빠져 있어 포함되지 않는다(두 번 내리면 수가 실제보다
     * 작아진다).
     */
    fun findQuestionIdsToDecrease(memberId: Long): List<Long>
}
