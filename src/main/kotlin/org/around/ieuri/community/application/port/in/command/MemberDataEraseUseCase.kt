package org.around.ieuri.community.application.port.`in`.command

/** 탈퇴 회원의 커뮤니티 데이터 삭제(SPEC-0011 R17). `MemberWithdrawn` 리스너가 부른다. */
interface MemberDataEraseUseCase {
    /** 그 회원의 글·댓글을 지운다. 지울 것이 없어도 오류가 아니다. */
    fun eraseMemberData(memberId: Long)
}
