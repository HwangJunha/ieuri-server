package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 관광지 좋아요/싫어요 기록 인바운드 포트(쓰기). 언어는 요청 스코프에서 결정돼 전달된다.
 * [memberId]는 인증된 회원(리졸버가 SecurityContext에서 확립). 비로그인은 리졸버 또는 여기서 거부한다.
 */
interface ReactToSpotUseCase {
    /**
     * **[language]가 저장 키의 일부다**(SPEC-0029 R-13) — 같은 장소라도 언어마다 별도의
     * 기록이며, 재평가는 같은 언어의 기록만 덮어쓴다.
     */
    fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale)
}
