package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 관광지 목록 정리 아웃바운드 포트(쓰기 전용). 적재(upsert)는 [SpotUpsertPort]가 맡는다. */
interface SpotStorePort {
    /**
     * [language]에서 [survivingContentIds]에 없는 행을 삭제하고 삭제 행 수를 돌려준다.
     * - 안전 판정을 하지 않는다 — 빈 집합으로 부르면 그 언어가 전량 삭제된다.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int
}
