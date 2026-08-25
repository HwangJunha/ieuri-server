package org.around.ieuri.auth.domain.vo

import java.time.Duration
import java.time.Instant

/**
 * 감사 기록의 보관 기간 판정(SPEC-0032 R22, POL-014).
 *
 * POL-014가 회원 감사 로그 1년을 정했으나 **삭제 경로가 아예 없어 정책이 집행되지 않고
 * 있었다.** 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 *
 * **기간을 인자로 받는다.** 위치 기록의 보관 기간은 위치정보법 신고 전이라 아직 확정할 근거가
 * 없고, 나중에 대상이 늘 때 판정 로직을 다시 쓰지 않으려면 기간이 밖에 있어야 한다.
 * `Message.RETENTION`의 선례와 같이 기간 **값** 자체는 정책이라 도메인이 소유한다.
 */
object AuditRetention {

    /** 회원 감사 로그 보관 기간(POL-014). 정책값이므로 설정으로 빼지 않는다. */
    val MEMBER_AUDIT: Duration = Duration.ofDays(365)

    /**
     * [retention]을 넘긴 기록의 경계 시각. 이보다 **이전**에 만들어진 행이 삭제 대상이다.
     */
    fun cutoff(now: Instant, retention: Duration): Instant = now.minus(retention)

    /**
     * [createdAt]이 보관 기간을 넘겼는지.
     *
     * **경계는 배타적이다** — 정확히 기간에 걸친 기록은 아직 삭제 대상이 아니다
     * (`Message.isExpired`와 같은 방향).
     */
    fun isExpired(createdAt: Instant, now: Instant, retention: Duration): Boolean =
        createdAt.isBefore(cutoff(now, retention))
}
