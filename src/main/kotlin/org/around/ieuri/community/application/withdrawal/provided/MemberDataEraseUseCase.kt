package org.around.ieuri.community.application.withdrawal.provided

/** 탈퇴 회원의 커뮤니티 데이터 정리. 탈퇴 커밋 뒤 리스너가 부른다. */
interface MemberDataEraseUseCase {
    fun eraseMemberData(memberId: Long)
}
