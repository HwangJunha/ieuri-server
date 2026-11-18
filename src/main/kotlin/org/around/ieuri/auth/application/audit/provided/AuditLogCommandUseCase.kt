package org.around.ieuri.auth.application.audit.provided

/**
 * 감사 기록 쓰기와 보관 기간 집행(SPEC-0032 R22, POL-014).
 * - 기록은 호출자의 트랜잭션과 따로 커밋한다 — 커밋 뒤 리스너와 비동기 스레드가 부른다.
 * - 파기는 호출 한 번이 한 회차다. 지운 건수가 상한보다 적으면 더 지울 것이 없다는 뜻이다.
 */
interface AuditLogCommandUseCase {
    fun recordMemberAudit(memberId: Long, eventType: String, payload: Map<String, Any?>)

    fun recordAccess(memberId: Long, operation: String, ipAddress: String?)

    fun recordLocationAccess(memberId: Long, payload: Map<String, Any?>)

    /** 회원 감사 기록(1년). */
    fun purgeExpiredBatch(limit: Int): Int

    /** 위치 조회 기록(6개월, 법정). */
    fun purgeExpiredLocationAccessBatch(limit: Int): Int

    /** 접속기록(1년, 법정). */
    fun purgeExpiredAccessLogBatch(limit: Int): Int
}
