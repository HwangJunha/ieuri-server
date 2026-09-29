package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale

/** upsert가 필요한 카탈로그 쓰기(SPEC-0019). 생 SQL(`INSERT ... ON CONFLICT`) 어댑터가 이 포트 뒤에 있다. */
interface SpotCatalogUpsertPort {

    /** 번역 한 건을 쓴다. 같은 `(catalogId, language)`가 이미 있으면 아무것도 하지 않는다(R-4). */
    fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
    )

    /** `spot_festival`에서 축제 기간을 확정한다. 축제 원본이 사라졌으면 기간도 지운다. */
    fun refreshFestivalPeriod(catalogId: Long)
}
