package org.around.ieuri.tourism.application.catalog.provided

import org.around.ieuri.tourism.application.catalog.provided.dto.ReactToSpotCommand
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 장소 카탈로그의 회원 쓰기 — 좋아요/싫어요. */
interface SpotCatalogCommandUseCase {
    /**
     * 반응을 기록한다. [language]가 저장 키의 일부라 재평가는 같은 언어의 기록만 덮어쓴다(SPEC-0029 R-13).
     * - 없는 카탈로그는 SPOT_NOT_FOUND, 이 언어에서 볼 수 없는 카탈로그는 SPOT_NOT_FOUND_IN_LOCALE이다.
     */
    fun react(command: ReactToSpotCommand, memberId: Long, language: DomainLocale)

    /** 탈퇴 정리 — 회원의 반응을 지운다. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
