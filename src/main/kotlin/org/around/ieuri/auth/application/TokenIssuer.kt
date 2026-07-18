package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.port.`in`.command.TokenPairResult
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.around.ieuri.auth.application.port.out.token.TokenProviderPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * accessToken/refreshToken 쌍을 발급하고 refresh jti를 저장소에 등록하는 공통 로직.
 * 로그인·재발급(회전) 양쪽에서 재사용한다.
 *
 * accessToken에는 발급 시점의 유효 권한(역할들의 권한 합집합)과 역할 코드를 담는다.
 * 재발급(회전) 시에도 현재 유효 권한을 재조회하므로 역할 변경이 자연히 전파된다(최대 access TTL 지연).
 */
@Component
class TokenIssuer(
    private val tokenProvider: TokenProviderPort,
    private val refreshTokenStore: RefreshTokenStorePort,
    private val roleRepository: RoleRepositoryPort,
    private val clock: Clock,
) {
    fun issueFor(memberId: Long): TokenPairResult {
        val roles = roleRepository.findRolesOfMember(memberId)
        val permissions = roles.flatMap { it.permissions }.map { it.code }.toSet()
        val roleCodes = roles.map { it.code }.toSet()

        val access = tokenProvider.issueAccessToken(memberId, permissions, roleCodes)
        val refresh = tokenProvider.issueRefreshToken(memberId)
        refreshTokenStore.save(refresh.jti, memberId, refresh.expiresAt)

        val now = clock.instant()
        return TokenPairResult(
            accessToken = access.token,
            refreshToken = refresh.token,
            tokenType = "Bearer",
            accessTokenExpiresInSeconds = Duration.between(now, access.expiresAt).seconds,
            refreshTokenExpiresInSeconds = Duration.between(now, refresh.expiresAt).seconds,
        )
    }
}
