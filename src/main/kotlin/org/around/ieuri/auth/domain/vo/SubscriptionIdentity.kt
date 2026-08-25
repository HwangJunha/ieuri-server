package org.around.ieuri.auth.domain.vo

import java.time.Duration
import java.time.Instant

/**
 * 소켓 신원이 새 구독을 열 자격이 있는지의 판정(SPEC-0032 R7, POL-048).
 *
 * **연결이 살아 있다는 것이 그 자원을 볼 자격의 증명은 아니다.** WebSocket은
 * `connection_init`에서 1회 인증하고 세션 속성에 신원을 담아 두는데, 그 속성에는 만료 개념이
 * 없어 로그아웃·정지·탈퇴 이후에도 연결 수명 내내 그 회원으로 스트리밍됐다.
 *
 * **기존 구독 스트림은 이 판정의 대상이 아니다.** 액세스 TTL이 5분이라 대화 중 반드시
 * 만료되는데 그때마다 연결을 끊으면 타이핑 표시가 주기적으로 사라진다 — 그래서 판정은
 * **새 `subscribe`에만** 적용한다. 이미 열린 스트림은 끊지 않는다.
 */
object SubscriptionIdentity {

    /**
     * 연결 시각으로부터 [accessTtl]을 넘겼는지. 경계(정확히 TTL)는 아직 초과가 아니다 —
     * 토큰 만료 판정과 같은 방향으로 맞춘다.
     */
    fun isExpired(connectedAt: Instant, now: Instant, accessTtl: Duration): Boolean =
        now.isAfter(connectedAt.plus(accessTtl))

    /**
     * 새 구독을 열 수 있는지. **둘 중 하나라도 걸리면 거부한다.**
     *
     * [hasActiveToken]이 false라는 것은 그 회원의 리프레시가 전부 폐기됐다는 뜻이다 —
     * 탈퇴·정지·재사용 감지가 `revokeAllByMember`를 부르므로 이 상태를 읽으면 된다.
     */
    fun canSubscribe(
        connectedAt: Instant,
        now: Instant,
        accessTtl: Duration,
        hasActiveToken: Boolean,
    ): Boolean = hasActiveToken && !isExpired(connectedAt, now, accessTtl)
}
