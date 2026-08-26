package org.around.ieuri.auth.domain.specification

import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/**
 * 감사 기록의 보관 기간 판정(SPEC-0032 R22, POL-014).
 *
 * POL-014가 회원 감사 로그 1년을 정했으나 **삭제 경로가 아예 없어 정책이 집행되지 않고
 * 있었다.** 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 *
 * **기간을 인자로 받는다.** 대상마다 기간이 다르고(회원 감사 1년 · 위치 기록 6개월), 대상이
 * 늘 때 판정 로직을 다시 쓰지 않으려면 기간이 밖에 있어야 한다. `Message.RETENTION`의 선례와
 * 같이 기간 **값** 자체는 정책이라 도메인이 소유한다.
 */
object AuditRetention {

    /** 회원 감사 로그 보관 기간(POL-014). 정책값이므로 설정으로 빼지 않는다. */
    val MEMBER_AUDIT: Duration = Duration.ofDays(365)

    /**
     * 접속기록 보관 기간(「개인정보의 안전성 확보조치 기준」 제8조 제2항).
     *
     * **법정 최소 기간이라 줄이면 위반이다.** 방침이 통신비밀보호법의 3개월을 근거로 들고
     * 있었으나 접속기록의 근거는 이쪽이고 기간이 더 길다.
     */
    val MEMBER_ACCESS: Period = Period.ofYears(1)

    /**
     * 위치정보 수집·이용·제공사실 확인자료 보관 기간.
     *
     * **위치정보법 제16조 제2항이 정하는 6개월이다** — 재량값이 아니므로 설정으로 빼지 않는다.
     * 개인정보처리방침 제3조·제10조와 위치기반서비스 이용약관 제5조·제6조가 이 기간을 고지하며,
     * 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
     *
     * **[Duration]이 아니라 [Period]다.** 법이 "6개월"로 정한 기간이라 달의 길이를 따라야 한다.
     * 180일 같은 고정 일수로 세면 달마다 경계가 어긋나 고지한 6개월보다 이르게 지운다
     * (`Message.RETENTION`이 180일인 것은 그쪽이 법정 기간이 아니라 운영 정책값이기 때문이다).
     */
    val LOCATION_ACCESS: Period = Period.ofMonths(6)

    /**
     * 개월로 정해진 기간을 셀 때 쓰는 시간대.
     *
     * **[Period]는 달력 위에서만 뺄 수 있어 시간대가 필요하다.** 사업자 소재지 기준이며,
     * 경계가 반나절 흔들리는 것은 6개월 보관에서 문제가 되지 않는다 — 중요한 것은 UTC로 세어
     * 실제 6개월보다 이르게 지우지 않는 것이다.
     */
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /**
     * [retention]을 넘긴 기록의 경계 시각. 이보다 **이전**에 만들어진 행이 삭제 대상이다.
     */
    fun cutoff(now: Instant, retention: Duration): Instant = now.minus(retention)

    /**
     * 개월 단위 보관 기간의 경계 시각. 규약은 위와 같다.
     *
     * 달의 길이를 따르므로 회차마다 경계 간격이 다르다 — 그것이 "6개월"의 의미다.
     */
    fun cutoff(now: Instant, retention: Period): Instant =
        now.atZone(ZONE).minus(retention).toInstant()

    /**
     * [createdAt]이 보관 기간을 넘겼는지.
     *
     * **경계는 배타적이다** — 정확히 기간에 걸친 기록은 아직 삭제 대상이 아니다
     * (`Message.isExpired`와 같은 방향).
     */
    fun isExpired(createdAt: Instant, now: Instant, retention: Duration): Boolean =
        createdAt.isBefore(cutoff(now, retention))
}
