package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 관광지 목록 정리 아웃바운드 포트. **쓰기 전용** — 조회는 읽기 포트가 따로 담당한다.
 *
 * 적재(upsert)는 생 SQL 경로라 [SpotUpsertPort]로 갈라져 있다.
 */
interface SpotStorePort {
    /**
     * [language]에서 [survivingContentIds]에 **없는** 행을 삭제한다.
     *
     * **호출자가 게이트를 통과시킨 뒤에만 부른다** — 이 메서드는 안전 판정을 하지 않으므로,
     * 빈 집합으로 부르면 그 언어가 전량 삭제된다.
     *
     * @return 삭제된 행 수.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int
}
