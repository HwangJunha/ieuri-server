package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.command.SpotActivationResult

/**
 * 비관광 spot 판정 인바운드 포트(SPEC-0018). 배치의 판정 단계가 호출한다.
 */
interface SpotActivationUseCase {
    /**
     * 적재된 전 행을 현재 키워드 설정으로 재판정해 활성 상태에 반영한다.
     *
     * **멱등하다** — 설정과 데이터가 같으면 몇 번을 불러도 결과가 같고, 두 번째 호출부터는
     * 전환 건수가 0이다. 판정 대상 범위는 실행 회차와 무관하게 항상 전량이다.
     */
    fun applyExclusions(): SpotActivationResult
}
