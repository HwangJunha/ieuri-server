package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.member.required.LoginIdentifierProtectorPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Email
import org.around.ieuri.auth.domain.member.Provider
import org.springframework.stereotype.Component

/**
 * `(provider, email)` 유니크 판정(SPEC-0024 R5). 가입·인증 주소 정정·이메일 변경이 함께 쓴다.
 * - 다른 provider의 같은 주소는 충돌이 아니다(POL-002). 조회 조건에 provider가 반드시 들어간다.
 * - 판정 키는 정규화된 주소의 결정론적 HMAC이다(암호문은 IV가 달라 등호 조회가 안 된다).
 */
@Component
class ProviderEmailUniqueness(
    private val memberRepository: MemberRepository,
    private val identifierProtector: LoginIdentifierProtectorPort,
) {
    /**
     * 같은 provider에 같은 주소가 이미 있으면 거부한다. 예외에 상대 회원의 정보를 담지 않는다(POL-013).
     * @param excludingMemberId 본인 제외 기준. 가입처럼 본인이 아직 없으면 null.
     */
    fun ensureAvailable(provider: Provider, email: Email, excludingMemberId: Long? = null) {
        if (memberRepository.existsByEmailIdentifier(provider, identifierProtector.protect(email.value), excludingMemberId)) {
            throw EmailAlreadyInUseException("이미 사용 중인 이메일입니다.")
        }
    }
}
