package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.domain.member.Provider

/** 로그인의 DB 단계. 호출 하나가 트랜잭션 하나다. 비밀번호 해시를 내보내므로 리졸버가 부르지 않는다 — [MemberLoginUseCase]만 쓴다. */
interface MemberAuthenticationUseCase {
    /**
     * SNS 신원의 회원을 로그인시킨다. 미가입은 MEMBER_NOT_FOUND, 유효한 정지는 ACCOUNT_INACTIVE다.
     * - 기간제 정지가 만료됐으면 복구하고 허용한다.
     */
    fun admitSns(provider: Provider, providerUserId: String): Long

    /** [loginIdentifier]는 정규화한 이메일의 보호 변환값이다. 없는 계정이면 null. */
    fun findEmailCredential(loginIdentifier: String): EmailCredentialView?

    /** 비밀번호 대조를 마친 회원을 로그인시킨다. 정지 판정은 [admitSns]와 같다. */
    fun admit(memberId: Long)

    /** 탈퇴 때 쓸 provider 토큰을 보관한다. 회원이 없거나 자체(EMAIL) 회원이면 아무것도 하지 않는다. */
    fun keepProviderRefreshToken(memberId: Long, token: String)
}
