package org.around.ieuri.auth.adapter.security

import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.common.security.EmailVerificationStatusPort
import org.springframework.stereotype.Component

/** 미인증 게이트(SPEC-0024 R9)가 읽는 이메일 인증 여부. 트랜잭션은 조회 유스케이스가 연다. */
@Component
class EmailVerificationStatusAdapter(
    private val memberQuery: MemberQueryUseCase,
) : EmailVerificationStatusPort {

    override fun isEmailVerified(memberId: Long): Boolean = memberQuery.isEmailVerified(memberId)
}
