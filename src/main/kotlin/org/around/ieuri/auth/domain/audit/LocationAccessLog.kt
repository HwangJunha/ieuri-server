package org.around.ieuri.auth.domain.audit

import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** 위치 조회 감사 기록 한 건(위치정보법 제16조). 좌표는 담지 않는다 — 맥락(연산명)만 남긴다. */
data class LocationAccessLog(
    val memberId: Long,
    val payload: Map<String, Any?>,
) {
    companion object {
        /**
         * 보관 기간(위치정보법 제16조 제2항). 법정값이라 설정으로 빼지 않는다.
         * - 일수가 아니라 개월이다. 180일로 세면 고지한 6개월보다 이르게 지운다.
         */
        val RETENTION: Period = Period.ofMonths(6)

        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** 이 시각보다 먼저 만들어진 기록이 파기 대상이다. 사업자 소재지 시간대로 센다. */
        fun cutoff(now: Instant): Instant = now.atZone(ZONE).minus(RETENTION).toInstant()
    }
}
