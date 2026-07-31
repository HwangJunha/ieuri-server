package org.around.ieuri.tourism.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 조회 인바운드 포트(CQRS 읽기). 언어는 요청 스코프에서 결정돼 명시 파라미터로 전달된다
 * (리졸버가 LocaleContext에서 읽어 넘긴다 — application은 서블릿/헤더를 모른다).
 */
interface SpotQueryUseCase {
    /** 위치 기반 관광지 커서 페이지. 정렬 (거리 ASC, id), 비관광 필터·거리계산은 하위 계층에서 완료. */
    fun spots(query: SpotsQuery, locale: DomainLocale): CursorPage<SpotView>

    /** 현재 언어에서 유효한 카테고리 목록(AC-8). */
    fun availableCategories(locale: DomainLocale): List<SpotCategory>
}
