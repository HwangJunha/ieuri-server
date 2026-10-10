package org.around.ieuri.auth.domain.audit

import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** 접속기록 한 건(안전성 확보조치 기준 제8조): 계정·접속일시·접속지·수행업무. */
data class MemberAccessLog(
    val memberId: Long,
    /** 수행업무 — GraphQL 루트 필드명이 들어간다. */
    val operation: String,
    /** 접속지. 프록시가 주지 않아 알 수 없으면 null이다. */
    val ipAddress: String?,
) {
    companion object {
        /** 보관 기간(기준 제8조 제2항). 법정 최소 기간이라 줄이면 위반이다. */
        val RETENTION: Period = Period.ofYears(1)

        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** 이 시각보다 먼저 만들어진 기록이 파기 대상이다. 사업자 소재지 시간대로 센다. */
        fun cutoff(now: Instant): Instant = now.atZone(ZONE).minus(RETENTION).toInstant()
    }
}
