package org.around.ieuri.community.application.port.out.persistence

/**
 * 탈퇴 회원의 본인만의 데이터(좋아요·숨김·차단) 물리 삭제(SPEC-0011 R17).
 * - 남에게 보이는 콘텐츠는 [MemberContentSoftDeletePort]가 소프트 삭제한다.
 */
interface MemberDataErasePort {
    /** 회원이 누른 좋아요. */
    fun deleteLikesByMember(memberId: Long): Long

    /** 회원이 건 숨김. */
    fun deleteHidesByMember(memberId: Long): Long

    /** 회원이 걸었거나 회원을 향한 차단. 양쪽 모두 지운다. */
    fun deleteBlocksInvolving(memberId: Long): Long
}
