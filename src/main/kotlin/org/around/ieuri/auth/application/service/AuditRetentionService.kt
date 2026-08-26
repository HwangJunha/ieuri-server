package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.port.`in`.command.AuditRetentionUseCase
import org.around.ieuri.auth.application.port.out.persistence.AccessLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogRetentionPort
import org.around.ieuri.auth.domain.specification.AuditRetention
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 *
 * POL-014가 회원 감사 로그 1년을 정했으나 **삭제 경로가 아예 없어 정책이 집행되지 않고
 * 있었다** — 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 *
 * **대상은 둘이고 기간이 다르다** — 회원 감사 로그 1년(POL-014)과 위치 조회 기록 6개월
 * (위치정보법 제16조 제2항). 판정 함수가 기간을 인자로 받으므로([AuditRetention]) 대상이 늘어도
 * 판정 로직은 그대로다.
 *
 * 위치 기록의 정리가 뒤늦게 붙은 이유를 남긴다: R21의 스위치가 꺼져 있어 정리할 행이 없다고
 * 보았으나 **그 스위치는 구현된 적이 없다**(`LocationAccessAuditListener`는 조건 없이 기록한다).
 * 기록은 실제로 쌓이고 있었고, 그동안 방침이 고지한 6개월 파기가 집행되지 않았다.
 *
 * `MessageRetentionService`의 선례를 따라 **@Transactional을 붙이지 않는다** — 트랜잭션 경계는
 * 포트 구현 안에 있고, 회차 단위로 끊겨야 락과 WAL이 커지지 않는다.
 */
@Service
class AuditRetentionService(
    private val auditLogRetentionPort: AuditLogRetentionPort,
    private val locationAccessLogRetentionPort: LocationAccessLogRetentionPort,
    private val accessLogRetentionPort: AccessLogRetentionPort,
    private val clock: Clock,
) : AuditRetentionUseCase {

    override fun purgeExpiredBatch(limit: Int): Int {
        val cutoff = AuditRetention.cutoff(clock.instant(), AuditRetention.MEMBER_AUDIT)
        return auditLogRetentionPort.deleteCreatedBefore(cutoff, limit)
    }

    override fun purgeExpiredLocationAccessBatch(limit: Int): Int {
        val cutoff = AuditRetention.cutoff(clock.instant(), AuditRetention.LOCATION_ACCESS)
        return locationAccessLogRetentionPort.deleteCreatedBefore(cutoff, limit)
    }

    override fun purgeExpiredAccessLogBatch(limit: Int): Int {
        val cutoff = AuditRetention.cutoff(clock.instant(), AuditRetention.MEMBER_ACCESS)
        return accessLogRetentionPort.deleteCreatedBefore(cutoff, limit)
    }
}
