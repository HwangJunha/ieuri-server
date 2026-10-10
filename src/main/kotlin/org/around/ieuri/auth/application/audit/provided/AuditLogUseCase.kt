package org.around.ieuri.auth.application.audit.provided

/** 감사 기록을 남긴다. 호출자의 트랜잭션과 따로 커밋한다 — 커밋 뒤 리스너와 비동기 스레드가 부른다. */
interface AuditLogUseCase {
    fun recordMemberAudit(memberId: Long, eventType: String, payload: Map<String, Any?>)

    fun recordAccess(memberId: Long, operation: String, ipAddress: String?)

    fun recordLocationAccess(memberId: Long, payload: Map<String, Any?>)
}
