package org.around.ieuri.auth.domain.token.spec

import org.around.ieuri.auth.domain.shared.Specification
import org.around.ieuri.auth.domain.token.SocketIdentity
import java.time.Duration

/**
 * 소켓 신원이 새 구독을 열 자격이 있는지(SPEC-0032 R7, POL-048). 토큰이 살아 있고 연결 수명이 [accessTtl]을 넘지 않아야 한다.
 * - 새 `subscribe`에만 적용한다. 이미 열린 스트림은 끊지 않는다.
 * - 경계(정확히 TTL)는 아직 초과가 아니다.
 */
class LiveSubscriptionIdentity(
    private val accessTtl: Duration,
) : Specification<SocketIdentity> {
    override fun isSatisfiedBy(candidate: SocketIdentity): Boolean = candidate.hasActiveToken && !candidate.checkedAt.isAfter(candidate.connectedAt.plus(accessTtl))
}
