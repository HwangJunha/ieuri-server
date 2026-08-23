package org.around.ieuri.tourism.domain.vo

import java.time.LocalDate

/**
 * 축제 행사 기간(SPEC-0007 R-4·R-8). 시작일과 종료일을 한 쌍으로 묶어, 둘의 관계에 대한 불변식과
 * **종료 판정 규칙을 도메인에 둔다.**
 *
 * 두 날짜를 따로 들고 다니면 "종료일이 시작일보다 앞설 수 없다"는 규칙이 검사되는 곳마다 흩어지고,
 * 종료 판정(`end < 기준일`)이 배치나 SQL에만 있으면 경계 조건이 두 곳에서 어긋난다. 특히
 * **종료일 == 실행일은 아직 유효**라는 경계(AC-27)가 한 곳에만 있어야 한다.
 *
 * 원본 `YYYYMMDD` 문자열이 아니라 [LocalDate]를 받는다 — 파싱은 어댑터의 책임이고, 도메인은
 * 이미 날짜로 해석된 값만 다룬다. 해석되지 않는 항목은 애초에 여기까지 오지 않는다(R-4).
 */
data class FestivalPeriod(
    val start: LocalDate,
    val end: LocalDate,
) {
    init {
        // AC-15. 기간이 뒤집힌 축제는 유효성 판정도 정리도 무의미하므로 존재를 허용하지 않는다.
        require(!end.isBefore(start)) { "행사 종료일은 시작일보다 앞설 수 없습니다." }
    }

    /**
     * [date] 기준으로 이미 끝난 행사인가(R-8).
     *
     * **종료일 당일은 아직 끝나지 않았다**(AC-27) — 오늘 끝나는 행사는 오늘 열려 있다.
     * 하루 뒤인 다음 배치가 지운다.
     */
    fun isEndedAsOf(date: LocalDate): Boolean = end.isBefore(date)
}
