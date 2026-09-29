package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 관광지 좋아요/싫어요 기록 유스케이스. */
interface ReactToSpotUseCase {
    /** 반응을 기록한다. [language]가 저장 키의 일부라 재평가는 같은 언어의 기록만 덮어쓴다(SPEC-0029 R-13). */
    fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale)
}
