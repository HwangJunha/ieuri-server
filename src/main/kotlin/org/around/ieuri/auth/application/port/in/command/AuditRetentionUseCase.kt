package org.around.ieuri.auth.application.port.`in`.command

/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 *
 * POL-014가 1년을 정했으나 삭제 경로가 아예 없어 **정책이 집행되지 않고 있었다.**
 * 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 *
 * **대상이 둘이고 기간이 다르다.** 회원 감사 로그는 1년(POL-014, 자율 정책값)이고, 위치 조회
 * 기록은 6개월(위치정보법 제16조 제2항, 법정 기간)이다. 한 메서드로 합치면 호출부가 어느
 * 기간으로 지우는지 고를 수 없다.
 */
interface AuditRetentionUseCase {
    /** 한 회차를 지우고 삭제 건수를 돌려준다. 상한보다 적게 지웠으면 더 지울 것이 없다는 뜻이다. */
    fun purgeExpiredBatch(limit: Int): Int

    /** 위치 조회 기록 한 회차. 규약은 위와 같고 기간만 다르다(6개월). */
    fun purgeExpiredLocationAccessBatch(limit: Int): Int

    /** 접속기록 한 회차. 규약은 위와 같고 기간만 다르다(1년). */
    fun purgeExpiredAccessLogBatch(limit: Int): Int
}
