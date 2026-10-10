package org.around.ieuri.auth.application.member.required

/** 회원의 로그인 세션(refresh 토큰) 폐기. 저장소에 닿지 못하면 예외를 던진다(fail-closed). */
interface MemberSessionPort {
    fun revokeAll(memberId: Long)
}
