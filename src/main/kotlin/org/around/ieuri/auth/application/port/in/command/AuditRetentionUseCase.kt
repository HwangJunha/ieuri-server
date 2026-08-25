package org.around.ieuri.auth.application.port.`in`.command

/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 *
 * POL-014가 1년을 정했으나 삭제 경로가 아예 없어 **정책이 집행되지 않고 있었다.**
 * 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 */
interface AuditRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int
}
