package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.RefreshTokenCommand
import org.around.ieuri.auth.application.exception.TokenReuseDetectedException
import org.around.ieuri.auth.application.port.`in`.command.RefreshTokenUseCase
import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.springframework.stereotype.Service

/**
 * refresh 토큰으로 accessToken 재발급(회전).
 * 저장소에 살아있는 jti만 통과시키고, 이미 회전/폐기된 refresh 재사용은 탈취로 보고 회원 전체를 폐기한다.
 */
@Service
class RefreshTokenHandler(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val tokenIssuer: TokenIssuer,
) : RefreshTokenUseCase {

    override fun handle(command: RefreshTokenCommand): TokenPairResult {
        // 서명/만료/타입 검증 실패 시 InvalidTokenException.
        val claims = tokenProvider.parseRefreshToken(command.refreshToken)

        if (!refreshTokenStore.existsActive(claims.jti)) {
            // 재사용 감지: 이미 회전·폐기된 refresh → 해당 회원 전체 폐기.
            refreshTokenStore.revokeAllByMember(claims.memberId)
            throw TokenReuseDetectedException("재사용된 리프레시 토큰입니다. 다시 로그인해 주세요.")
        }

        // 회전: 이전 refresh 폐기 후 새 토큰 쌍 발급.
        refreshTokenStore.revoke(claims.jti)
        return tokenIssuer.issueFor(claims.memberId)
    }
}
