package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.domain.member.Provider

/** 회원 인증. 통과하면 회원 id를 돌려준다 — 토큰 발급은 호출자가 한다. */
interface MemberLoginUseCase {
    /** id_token을 검증해 기존 회원을 인증한다(미가입은 거부). authorization code가 있으면 provider 토큰을 보충한다. */
    fun loginBySns(provider: Provider, idToken: String, authorizationCode: String?): Long

    /** 이메일·비밀번호로 인증한다. 계정이 없든 비밀번호가 틀리든 INVALID_CREDENTIALS 하나로 답한다. */
    fun loginByEmail(email: String, password: String): Long
}
