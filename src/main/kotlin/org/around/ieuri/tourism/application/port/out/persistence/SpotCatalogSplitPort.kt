package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/** 잘못 접힌 카탈로그를 가르는 교정용 쓰기 포트(SPEC-0029). */
interface SpotCatalogSplitPort {

    /** spot 행 여럿을 다른 카탈로그로 옮긴다. 이미 배정된 행을 덮어쓴다. */
    fun reassign(spotIds: List<Long>, catalogId: Long)

    /**
     * 옮겨 간 언어의 번역 행을 옛 카탈로그에서 지운다.
     * - 감춤(`visible=false`)이 아니라 삭제다.
     */
    fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>)
}
