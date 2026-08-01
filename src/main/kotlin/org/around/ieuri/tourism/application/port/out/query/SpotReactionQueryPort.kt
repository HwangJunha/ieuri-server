package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 반응 기록 읽기 아웃바운드 포트(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * 이미 반응한 관광지를 spots 응답에서 제외(중복 노출 방지)하는 데 쓴다.
 */
interface SpotReactionQueryPort {
    /**
     * 주어진 contentId들 중 이 회원이 (현재 언어에서) 이미 반응한 것의 집합.
     * 조회 범위를 후보 contentId로 좁혀(IN) 회원의 전체 반응을 끌어오지 않는다.
     */
    fun findReactedContentIds(memberId: Long, contentIds: Collection<String>, language: DomainLocale): Set<String>

    /**
     * 이 contentId·언어에 대한 LIKE 반응 수(SPEC-0003 §5). DISLIKE는 세지 않고, 언어를 넘어 합산하지 않는다
     * (키가 (content_id, language)라 언어별 분리 — SPEC-0002의 알려진 한계 상속). 반응이 없으면 0.
     * 상세 조회 시 캐시와 무관하게 매번 실시간 집계한다(likeCount는 캐시 비대상).
     */
    fun countLikes(contentId: String, language: DomainLocale): Long
}
