package org.around.ieuri.auth.application.port.out.token

import java.time.Instant

/** refresh 토큰(jti) 저장소 포트. 회전·폐기·재사용 감지를 위해 살아있는 jti를 추적한다. */
interface RefreshTokenStorePort {
    fun save(jti: String, memberId: Long, expiresAt: Instant)

    /** 폐기되지 않고 만료되지 않은 jti인지. */
    fun existsActive(jti: String): Boolean

    fun revoke(jti: String)

    /** 재사용 감지 등으로 해당 회원의 모든 refresh를 폐기. */
    fun revokeAllByMember(memberId: Long)

    /**
     * 이 회원에게 살아 있는 refresh가 하나라도 있는지(SPEC-0032 R7).
     * - false는 탈퇴·정지·재사용 감지로 전부 폐기됐다는 뜻이다(셋 다 [revokeAllByMember]를 부른다).
     */
    fun hasActiveToken(memberId: Long): Boolean
}
