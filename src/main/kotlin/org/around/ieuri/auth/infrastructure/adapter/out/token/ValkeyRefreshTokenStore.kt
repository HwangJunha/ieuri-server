package org.around.ieuri.auth.infrastructure.adapter.out.token

import org.around.ieuri.auth.application.port.out.token.RefreshTokenStorePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Valkey(Redis 프로토콜 호환) 기반 refresh 토큰 저장소. 분산·재시작 안전.
 *
 * 키 스키마:
 *   - jti 인덱스:    `ieuri:auth:refresh:jti:{jti}`      → memberId(문자열), TTL = refresh 만료까지
 *   - 회원 인덱스:   `ieuri:auth:refresh:member:{id}`    → Set<jti>, 전체 폐기(재사용 감지)용
 *
 * 만료는 Valkey 네이티브 TTL로 자동 축출한다(existsActive는 EXISTS 한 번). 회원 Set 멤버는 개별 TTL이
 * 없어 stale이 쌓일 수 있으나 revokeAllByMember가 최종 정리하며, Set 자체도 save마다 TTL을 갱신해
 * 최신 refresh TTL만큼만 살아있게 한다. 다중 키 조작은 순차(best-effort) 실행한다.
 *
 * 인프라 예외(연결 끊김 등)는 잡지 않고 그대로 전파해 GraphQL 최상위 error로 클라이언트에 전달한다.
 * 특히 existsActive는 장애를 false로 삼키지 않는다(재사용 감지 오작동 방지, fail-closed).
 */
@Component
class ValkeyRefreshTokenStore(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) : RefreshTokenStorePort {

    private fun jtiKey(jti: String) = "$JTI_PREFIX$jti"
    private fun memberKey(memberId: Long) = "$MEMBER_PREFIX$memberId"

    override fun save(jti: String, memberId: Long, expiresAt: Instant) {
        val ttl = Duration.between(clock.instant(), expiresAt)
        // 이미 만료된 토큰은 저장하지 않는다(음수 TTL로 SET하면 예외/즉시 만료).
        if (ttl.isZero || ttl.isNegative) return

        val memberKey = memberKey(memberId)
        redis.opsForValue().set(jtiKey(jti), memberId.toString(), ttl)
        redis.opsForSet().add(memberKey, jti)
        // 회원 Set이 영구히 남지 않도록 최신 refresh TTL로 만료를 갱신한다.
        redis.expire(memberKey, ttl)
    }

    override fun existsActive(jti: String): Boolean =
        redis.hasKey(jtiKey(jti)) == true

    override fun revoke(jti: String) {
        val memberId = redis.opsForValue().get(jtiKey(jti)) ?: return
        redis.delete(jtiKey(jti))
        redis.opsForSet().remove(memberKey(memberId.toLong()), jti)
    }

    override fun revokeAllByMember(memberId: Long) {
        val memberKey = memberKey(memberId)
        val jtis = redis.opsForSet().members(memberKey)
        if (!jtis.isNullOrEmpty()) {
            redis.delete(jtis.map { jtiKey(it) })
        }
        redis.delete(memberKey)
    }

    private companion object {
        const val JTI_PREFIX = "ieuri:auth:refresh:jti:"
        const val MEMBER_PREFIX = "ieuri:auth:refresh:member:"
    }
}
