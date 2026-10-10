package org.around.ieuri.auth.application.audit.provided
/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 * - 대상마다 기간이 다르다: 회원 감사 로그 1년, 위치 조회 기록 6개월(법정), 접속기록 1년(법정).
 */
interface AuditRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int

    /** 위치 조회 기록 한 회차. 규약은 위와 같고 기간만 다르다(6개월). */
    fun purgeExpiredLocationAccessBatch(limit: Int): Int

    /** 접속기록 한 회차. 규약은 위와 같고 기간만 다르다(1년). */
    fun purgeExpiredAccessLogBatch(limit: Int): Int
}
