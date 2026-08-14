package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 쓰기 포트(SPEC-0019). 배정 배치와 번역 배치가 쓴다.
 *
 * **매칭 후보 조회가 여기 없다.** 배치가 KO 제목 맵을 메모리에 들고 대조하므로 spot 한 행마다
 * DB를 왕복하지 않는다 — 그래서 조회용 인덱스도 저장 컬럼도 필요 없어졌다.
 */
interface SpotCatalogStorePort {

    /** 카탈로그를 저장하고 부여된 id를 돌려준다. 번역과 축제 기간도 함께 쓴다. */
    fun save(catalog: SpotCatalog): Long

    /** spot 행을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다. */
    fun assign(spotId: Long, catalogId: Long)

    /**
     * 번역 한 건을 쓴다. 같은 `(catalogId, language)`가 이미 있으면 **아무것도 하지 않는다** —
     * 먼저 들어온 값이 TourAPI 원문이고 그것이 우선한다(R-4).
     */
    fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?)

    /**
     * 축제 기간을 확정한다. **신규 카탈로그를 만든 직후에 부른다.**
     *
     * 축제 기간은 카탈로그를 만든 그 행이 아니라 `spot_festival`에서 오므로, 생성 시점에
     * 심지 않으면 그 축제는 기간을 갖지 못한다.
     */
    fun refreshFestivalPeriod(catalogId: Long)
}
