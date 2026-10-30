package org.around.ieuri.auth.application.audit

import org.around.ieuri.auth.application.audit.provided.AuditLogCommandUseCase
import org.around.ieuri.auth.application.audit.required.AuditLogRepository
import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 감사 기록 쓰기와 보관 기간 집행(SPEC-0032 R22, POL-014).
 * - 기록은 `REQUIRES_NEW`다. 커밋 뒤 리스너가 부르므로 `REQUIRED`면 이미 끝난 트랜잭션에 붙어 조용히 사라진다.
 * - 파기는 호출 한 번이 한 회차이고 회차마다 커밋한다.
 */
@Service
@Transactional
class AuditLogModifyService(
    private val repository: AuditLogRepository,
    private val clock: Clock,
) : AuditLogCommandUseCase {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordMemberAudit(memberId: Long, eventType: String, payload: Map<String, Any?>) {
        repository.insert(MemberAuditLog(memberId, eventType, payload))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordAccess(memberId: Long, operation: String, ipAddress: String?) {
        repository.insert(MemberAccessLog(memberId, operation, ipAddress))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordLocationAccess(memberId: Long, payload: Map<String, Any?>) {
        repository.insert(LocationAccessLog(memberId, payload))
    }

    override fun purgeExpiredBatch(limit: Int): Int = repository.deleteMemberAuditBefore(MemberAuditLog.cutoff(clock.instant()), limit)

    override fun purgeExpiredLocationAccessBatch(limit: Int): Int = repository.deleteLocationAccessBefore(LocationAccessLog.cutoff(clock.instant()), limit)

    override fun purgeExpiredAccessLogBatch(limit: Int): Int = repository.deleteAccessBefore(MemberAccessLog.cutoff(clock.instant()), limit)
}
