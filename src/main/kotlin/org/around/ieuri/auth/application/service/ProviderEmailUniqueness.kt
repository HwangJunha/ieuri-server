package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.springframework.stereotype.Component

/**
 * `(provider, email)` 유니크 판정(SPEC-0024 R5). 가입·인증 주소 재입력이 함께 쓴다.
 * - 다른 provider의 같은 주소는 충돌이 아니다(POL-002). 조회 조건에 provider가 반드시 들어간다.
 * - 판정 키는 정규화된 주소의 결정론적 HMAC이다(암호문은 IV가 달라 등호 조회가 안 된다).
 */
@Component
class ProviderEmailUniqueness(
    private val memberRepository: MemberRepositoryPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) {
    /** 유니크 판정에 쓰는 파생 식별자. 저장 시에도 같은 값을 써야 제약과 어긋나지 않는다. */
    fun identifierOf(email: Email): String = identifierProtector.protect(email.value)

    /**
     * 같은 provider에 같은 주소가 이미 있으면 거부한다. 예외에 상대 회원의 정보를 담지 않는다(POL-013).
     * @param excludingMemberId 본인 제외 기준. 가입처럼 본인이 아직 없으면 null.
     */
    fun ensureAvailable(provider: Provider, email: Email, excludingMemberId: Long? = null) {
        if (memberRepository.existsByProviderAndEmailIdentifier(provider, identifierOf(email), excludingMemberId)) {
            throw EmailAlreadyInUseException("이미 사용 중인 이메일입니다.")
        }
    }
}
