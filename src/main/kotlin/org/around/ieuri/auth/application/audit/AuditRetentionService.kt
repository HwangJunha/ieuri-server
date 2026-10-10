package org.around.ieuri.auth.application.audit
import org.around.ieuri.auth.application.audit.provided.AuditRetentionUseCase
import org.around.ieuri.auth.application.port.out.persistence.AccessLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.AuditLogRetentionPort
import org.around.ieuri.auth.application.port.out.persistence.LocationAccessLogRetentionPort
import org.around.ieuri.auth.domain.audit.AuditRetention
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 * - @Transactional을 붙이지 않는다. 트랜잭션은 포트 구현 안에서 회차 단위로 끊긴다.
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
