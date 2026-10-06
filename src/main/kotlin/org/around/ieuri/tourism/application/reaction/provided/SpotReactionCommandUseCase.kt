package org.around.ieuri.tourism.application.reaction.provided

import org.around.ieuri.tourism.application.reaction.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 관광지 좋아요/싫어요 쓰기. */
interface SpotReactionCommandUseCase {
    /** 반응을 기록한다. [language]가 저장 키의 일부라 재평가는 같은 언어의 기록만 덮어쓴다(SPEC-0029 R-13). */
    fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale)

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
