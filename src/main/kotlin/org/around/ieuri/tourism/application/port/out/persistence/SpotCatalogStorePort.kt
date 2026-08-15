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

    /**
     * **원본이 사라진 카탈로그의 번역 행을 지운다.** 지운 행 수를 돌려준다.
     *
     * 상류에서 장소가 없어지면 동기화가 `spot` 행을 지우지만 카탈로그와 번역은 남는다.
     * 그대로 두면 목록·지도·검색에는 보이는데 상세를 열면 실패하는 장소가 된다.
     *
     * **카탈로그 자체는 남긴다** — `spot_reaction`의 FK가 CASCADE라 카탈로그를 지우면 그
     * 장소의 좋아요가 함께 사라진다. 번역만 지우면 조회에서 빠지면서 좋아요는 보존되고,
     * 상류가 그 장소를 다시 주면 배정이 번역을 되살린다.
     *
     * 조회 쪽에 `EXISTS` 조건을 다는 대신 이 방법을 쓰는 이유는 **검색이 평면 질의라
     * 조인을 걸 수 없기 때문이다** — 목록은 걸러지는데 건수는 안 걸러져 빈 페이지가
     * 무한히 이어졌다.
     */
    fun purgeOrphanTranslations(): Int
}
