package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * upsert가 필요한 카탈로그 쓰기(SPEC-0019). **생 SQL 어댑터가 이 포트 뒤에 있다** —
 * JPA에 이식 가능한 upsert가 없어 `INSERT ... ON CONFLICT`를 써야 하기 때문이다.
 *
 * 포트를 따로 두는 이유는 `infrastructure → infrastructure` 주입을 막기 위해서다. 어댑터가
 * 다른 어댑터를 구체 타입으로 주입받으면 "어느 경로가 생 SQL을 쓰는가"가 애플리케이션에서
 * 보이지 않는다.
 */
interface SpotCatalogUpsertPort {

    /**
     * 번역 한 건을 쓴다. 같은 `(catalogId, language)`가 이미 있으면 **아무것도 하지 않는다** —
     * 먼저 들어온 값이 TourAPI 원문이고 그것이 우선한다(R-4).
     */
    fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?)

    /**
     * 축제 기간을 확정한다. 축제 원본이 사라졌으면 기간도 지운다.
     *
     * 축제 기간은 카탈로그를 만든 그 행이 아니라 `spot_festival`에서 오므로, 생성 시점에
     * 심지 않으면 그 축제는 기간을 갖지 못한다.
     */
    fun refreshFestivalPeriod(catalogId: Long)
}
