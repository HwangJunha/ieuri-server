package org.around.ieuri.auth.application.port.out.token

import java.time.Instant

/**
 * refresh 토큰(jti) 저장소 포트. 회전·폐기·재사용 감지를 위해 살아있는 jti를 추적한다.
 * 지금은 In-memory 구현이며, 시그니처 변경 없이 Redis/Valkey 구현으로 교체할 수 있다.
 */
interface RefreshTokenStorePort {
    fun save(jti: String, memberId: Long, expiresAt: Instant)

    /** 폐기되지 않고 만료되지 않은 jti인지. */
    fun existsActive(jti: String): Boolean

    fun revoke(jti: String)

    /** 재사용 감지 등으로 해당 회원의 모든 refresh를 폐기. */
    fun revokeAllByMember(memberId: Long)
}
