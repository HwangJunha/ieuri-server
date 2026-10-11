package org.around.ieuri.auth.application.token.provided

import org.around.ieuri.auth.application.token.provided.dto.AccessTokenClaims
import java.time.Instant

/** 요청의 신원 확인. 요청마다 불리며 DB를 읽지 않는다. */
interface AuthTokenQueryUseCase {
    /** accessToken의 인증 주체와 권한. 서명·만료·타입이 틀리면 `InvalidTokenException`이다. */
    fun authenticate(accessToken: String): AccessTokenClaims

    /** [connectedAt]에 맺은 소켓으로 지금 새 구독을 열 수 있는가(SPEC-0032 R7). 토큰이 전부 폐기됐거나 연결이 액세스 TTL을 넘겼으면 false다. */
    fun canOpenSubscription(memberId: Long, connectedAt: Instant): Boolean
}
