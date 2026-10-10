package org.around.ieuri.auth.infrastructure.adapter.out.token

import org.around.ieuri.auth.application.token.provided.exception.AuthStoreUnavailableException
import org.around.ieuri.auth.application.token.required.RefreshTokenStorePort
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Valkey 기반 refresh 토큰 저장소.
 * - `ieuri:auth:refresh:jti:{jti}` → memberId, TTL = refresh 만료까지
 * - `ieuri:auth:refresh:member:{id}` → Set<jti>(전체 폐기용). 멤버에 개별 TTL이 없어 stale jti가 남을 수 있다.
 * - 다중 키 조작은 순차(best-effort)다.
 * - 인프라 예외는 삼키지 않고 [AuthStoreUnavailableException]으로 옮긴다(SPEC-0032 R6). 장애를 false로 삼키지 않는다(fail-closed).
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
        translatingFailure {
            redis.opsForValue().set(jtiKey(jti), memberId.toString(), ttl)
            redis.opsForSet().add(memberKey, jti)
            // 회원 Set이 영구히 남지 않도록 최신 refresh TTL로 만료를 갱신한다.
            redis.expire(memberKey, ttl)
        }
    }

    override fun existsActive(jti: String): Boolean = translatingFailure { redis.hasKey(jtiKey(jti)) == true }

    override fun revoke(jti: String) {
        translatingFailure {
            val memberId = redis.opsForValue().get(jtiKey(jti)) ?: return@translatingFailure
            redis.delete(jtiKey(jti))
            redis.opsForSet().remove(memberKey(memberId.toLong()), jti)
        }
    }

    /** 회원 인덱스에 살아 있는 jti가 있는지(SPEC-0032 R7). Set에 stale이 남을 수 있어 실제 jti 키로 확인한다. */
    override fun hasActiveToken(memberId: Long): Boolean = translatingFailure {
        val jtis = redis.opsForSet().members(memberKey(memberId))
        if (jtis.isNullOrEmpty()) return@translatingFailure false
        jtis.any { redis.hasKey(jtiKey(it)) == true }
    }

    override fun revokeAllByMember(memberId: Long) {
        val memberKey = memberKey(memberId)
        translatingFailure {
            val jtis = redis.opsForSet().members(memberKey)
            if (!jtis.isNullOrEmpty()) {
                redis.delete(jtis.map { jtiKey(it) })
            }
            redis.delete(memberKey)
        }
    }

    /** 저장소 접근 실패를 [AuthStoreUnavailableException]으로 옮긴다. 메시지에 키나 jti를 담지 않는다(POL-013). */
    private fun <T> translatingFailure(block: () -> T): T = try {
        block()
    } catch (e: DataAccessException) {
        throw AuthStoreUnavailableException("인증 저장소에 일시적으로 접근할 수 없습니다.", e)
    }

    private companion object {
        const val JTI_PREFIX = "ieuri:auth:refresh:jti:"
        const val MEMBER_PREFIX = "ieuri:auth:refresh:member:"
    }
}
