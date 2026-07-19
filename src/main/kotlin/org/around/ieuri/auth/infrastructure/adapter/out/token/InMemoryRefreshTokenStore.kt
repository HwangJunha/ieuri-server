package org.around.ieuri.auth.infrastructure.adapter.out.token

import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory refresh 토큰 저장소(개발/임시). 단일 인스턴스 메모리라 서버 재시작 시 소실된다(재로그인 필요).
 * 만료는 조회 시 지연 정리한다. 회원별 인덱스로 전체 폐기(재사용 감지)를 지원한다.
 *
 * TODO(운영): RedisRefreshTokenStore / ValkeyRefreshTokenStore로 교체.
 *   - 키 refresh:{jti} → memberId, TTL = refresh 만료
 *   - 회원별 폐기는 member:{id}:jti set 유지
 *   RefreshTokenStorePort 시그니처는 그대로 두고 이 구현만 교체한다.
 */
@Component
class InMemoryRefreshTokenStore(
    private val clock: Clock,
) : RefreshTokenStorePort {

    private data class Entry(val memberId: Long, val expiresAt: Instant)

    private val byJti = ConcurrentHashMap<String, Entry>()
    private val jtisByMember = ConcurrentHashMap<Long, MutableSet<String>>()

    override fun save(jti: String, memberId: Long, expiresAt: Instant) {
        byJti[jti] = Entry(memberId, expiresAt)
        jtisByMember.computeIfAbsent(memberId) { ConcurrentHashMap.newKeySet() }.add(jti)
    }

    override fun existsActive(jti: String): Boolean {
        val entry = byJti[jti] ?: return false
        if (!clock.instant().isBefore(entry.expiresAt)) {
            remove(jti, entry.memberId)
            return false
        }
        return true
    }

    override fun revoke(jti: String) {
        val entry = byJti.remove(jti) ?: return
        jtisByMember[entry.memberId]?.remove(jti)
    }

    override fun revokeAllByMember(memberId: Long) {
        val jtis = jtisByMember.remove(memberId) ?: return
        jtis.forEach { byJti.remove(it) }
    }

    private fun remove(jti: String, memberId: Long) {
        byJti.remove(jti)
        jtisByMember[memberId]?.remove(jti)
    }
}
