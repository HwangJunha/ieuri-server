package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.exception.EmailAlreadyInUseException
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.security.LoginIdentifierProtectorPort
import org.around.ieuri.auth.domain.vo.Email
import org.around.ieuri.auth.domain.vo.Provider
import org.springframework.stereotype.Component

/**
 * `(provider, email)` 유니크 판정(SPEC-0024 R5). 가입·인증 주소 재입력이 함께 쓴다.
 *
 * **다른 provider에 같은 주소가 있는 것은 충돌이 아니다** — POL-002가 다계정을 허용하므로
 * `(KAKAO, a@b.com)`과 `(EMAIL, a@b.com)`은 공존한다. 조회 조건에 provider가 반드시 들어간다.
 *
 * 저장된 이메일 암호문은 IV가 매번 달라 등호 조회가 되지 않으므로, 정규화된 주소의 결정론적
 * HMAC을 유니크 판정용 파생 값으로 쓴다(`provider_user_id`가 푼 것과 같은 문제, SPEC-0012 R1).
 * 정규화는 [Email]이 이미 trim+lowercase로 수행한다(R5-5).
 */
@Component
class ProviderEmailUniqueness(
    private val memberRepository: MemberRepositoryPort,
    private val identifierProtector: LoginIdentifierProtectorPort,
) {
    /** 유니크 판정에 쓰는 파생 식별자. 저장 시에도 같은 값을 써야 제약과 어긋나지 않는다. */
    fun identifierOf(email: Email): String = identifierProtector.protect(email.value)

    /**
     * 같은 provider에 같은 주소가 이미 있으면 거부한다.
     *
     * 예외에 상대 회원의 정보를 담지 않는다(R5-4·R7-4, POL-013) — 누가 쓰는지는 호출자가
     * 알 필요가 없고, 담으면 이메일 존재 여부를 캐내는 통로가 된다.
     *
     * @param excludingMemberId 본인 제외 기준. 가입처럼 본인이 아직 없으면 null.
     */
    fun ensureAvailable(provider: Provider, email: Email, excludingMemberId: Long? = null) {
        if (memberRepository.existsByProviderAndEmailIdentifier(provider, identifierOf(email), excludingMemberId)) {
            throw EmailAlreadyInUseException("이미 사용 중인 이메일입니다.")
        }
    }
}
