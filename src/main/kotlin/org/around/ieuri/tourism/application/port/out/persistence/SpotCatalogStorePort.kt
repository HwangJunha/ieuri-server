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
    fun upsertTranslation(
        catalogId: Long,
        language: DomainLocale,
        title: String,
        address: String?,
    )

    /**
     * 축제 기간을 확정한다. **신규 카탈로그를 만든 직후에 부른다.**
     *
     * 축제 기간은 카탈로그를 만든 그 행이 아니라 `spot_festival`에서 오므로, 생성 시점에
     * 심지 않으면 그 축제는 기간을 갖지 못한다.
     */
    fun refreshFestivalPeriod(catalogId: Long)

    /**
     * **원본 상태를 번역 행의 `visible`에 반영한다.** 바뀐 (카탈로그, 언어)를 돌려준다.
     *
     * `visible`은 **그 언어에 원본이 있는가**다(SPEC-0029). 이전에는 카탈로그 단위 노출
     * 여부였고 언어별 판정은 `has_origin`이 따로 들었는데, 같은 사실을 두 단위로 말하는
     * 컬럼이 둘이라 조회가 늘 둘을 AND 해야 했다. 하나로 합쳤다.
     *
     * 상류에서 장소가 없어지면 동기화가 `spot` 행을 지우지만 카탈로그와 번역은 남는다.
     * 그대로 두면 목록·지도·검색에는 보이는데 상세를 열면 실패하는 장소가 된다.
     *
     * **지우지 않고 감춘다.** 번역 행을 삭제하면 배정 배치의 KO 제목 맵이 그 카탈로그를
     * 못 찾아, 상류가 장소를 다시 줬을 때 **새 카탈로그가 생기고 좋아요가 달린 옛 카탈로그는
     * 영원히 고아로 남는다** — 좋아요를 지키려고 카탈로그를 남긴 판단이 무의미해진다.
     *
     * 반대로 원본이 돌아온 카탈로그는 다시 보이게 한다. 그래서 이 메서드는 양방향이다.
     *
     * 조회 쪽에 `EXISTS` 조건을 다는 대신 이 방법을 쓰는 이유는 **검색이 평면 질의라
     * 조인을 걸 수 없기 때문이다** — 목록은 걸러지는데 건수는 안 걸러져 빈 페이지가
     * 무한히 이어졌다.
     */
    fun refreshTranslationVisibility(): CatalogVisibilityChanges
}

/** 노출 여부가 바뀐 (카탈로그, 언어). 다른 도메인이 자기 사본을 맞추는 근거다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: DomainLocale,
)

/**
 * 노출 갱신의 결과.
 *
 * [hidden]·[shown]은 이번에 **상태가 뒤집힌** 행이고, [visible]은 갱신 후 **노출 중인 전부**다.
 *
 * **구독자에게는 [visible]이 정본이다.** 뒤집힌 것만 실으면 사본을 채울 수 없다 — 전량
 * 초기화 후 재적재하면 모든 행이 신규 INSERT라 뒤집힌 적이 없고, 그러면 이벤트가 영원히
 * 비어 사본이 빈 채로 남는다. [hidden]·[shown]은 로그와 관측용이다.
 */
data class CatalogVisibilityChanges(
    val hidden: List<CatalogLanguage>,
    val shown: List<CatalogLanguage>,
    val visible: List<CatalogLanguage>,
) {
    val changedCount: Int get() = hidden.size + shown.size
}
