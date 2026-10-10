package org.around.ieuri.auth.domain.audit
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** 감사 기록 보관 기간 판정(SPEC-0032 R22, POL-014). 기간은 인자로 받고, 기간 값은 정책이라 여기서 소유한다. */
object AuditRetention {

    /** 회원 감사 로그 보관 기간(POL-014). 정책값이므로 설정으로 빼지 않는다. */
    val MEMBER_AUDIT: Duration = Duration.ofDays(365)

    /** 접속기록 보관 기간(안전성 확보조치 기준 제8조 제2항). 법정 최소 기간이라 줄이면 위반이다. */
    val MEMBER_ACCESS: Period = Period.ofYears(1)

    /**
     * 위치정보 수집·이용·제공사실 확인자료 보관 기간(위치정보법 제16조 제2항). 법정값이라 설정으로 빼지 않는다.
     * - [Duration]이 아니라 [Period]다. 180일 같은 고정 일수로 세면 고지한 6개월보다 이르게 지운다.
     */
    val LOCATION_ACCESS: Period = Period.ofMonths(6)

    /** 개월 단위 기간을 셀 때 쓰는 시간대(사업자 소재지). UTC로 세어 6개월보다 이르게 지우지 않게 한다. */
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** [retention]을 넘긴 기록의 경계 시각. 이보다 이전에 만들어진 행이 삭제 대상이다. */
    fun cutoff(now: Instant, retention: Duration): Instant = now.minus(retention)

    /** 개월 단위 보관 기간의 경계 시각. 달의 길이를 따르므로 회차마다 간격이 다르다. */
    fun cutoff(now: Instant, retention: Period): Instant = now.atZone(ZONE).minus(retention).toInstant()

    /** [createdAt]이 보관 기간을 넘겼는지. 경계는 배타적이다 — 정확히 기간에 걸친 기록은 아직 대상이 아니다. */
    fun isExpired(createdAt: Instant, now: Instant, retention: Duration): Boolean = createdAt.isBefore(cutoff(now, retention))
}
