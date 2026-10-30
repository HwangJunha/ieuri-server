package org.around.ieuri.auth.domain.token
import java.time.Duration
import java.time.Instant

/**
 * 소켓 신원이 새 구독을 열 자격이 있는지 판정(SPEC-0032 R7, POL-048).
 * - 새 `subscribe`에만 적용한다. 이미 열린 스트림은 끊지 않는다.
 */
object SubscriptionIdentity {

    /** 연결 시각으로부터 [accessTtl]을 넘겼는지. 경계(정확히 TTL)는 아직 초과가 아니다. */
    fun isExpired(connectedAt: Instant, now: Instant, accessTtl: Duration): Boolean = now.isAfter(connectedAt.plus(accessTtl))

    /** 새 구독을 열 수 있는지. 둘 중 하나라도 걸리면 거부한다. [hasActiveToken]=false는 리프레시가 전부 폐기됐다는 뜻이다. */
    fun canSubscribe(
        connectedAt: Instant,
        now: Instant,
        accessTtl: Duration,
        hasActiveToken: Boolean,
    ): Boolean = hasActiveToken && !isExpired(connectedAt, now, accessTtl)
}
