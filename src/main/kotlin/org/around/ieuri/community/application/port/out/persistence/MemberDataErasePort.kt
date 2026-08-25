package org.around.ieuri.community.application.port.out.persistence

/**
 * 탈퇴 회원의 커뮤니티 데이터 **물리 삭제**(SPEC-0011 R17).
 *
 * **여기 남은 것은 본인만의 데이터다** — 좋아요·숨김·차단. 남에게도 보이는 것
 * (글·댓글·질문·답변·답글·DM)은 지우면 남의 화면이 깨지므로 소프트 삭제로 옮겼다
 * ([MemberContentSoftDeletePort]).
 */
interface MemberDataErasePort {
    /** 회원이 누른 좋아요. */
    fun deleteLikesByMember(memberId: Long): Long

    /** 회원이 건 숨김. */
    fun deleteHidesByMember(memberId: Long): Long

    /** 회원이 걸었거나 회원을 향한 차단. 양쪽 모두 지운다. */
    fun deleteBlocksInvolving(memberId: Long): Long
}
