package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 쓰기 포트(SPEC-0019). 배정 배치가 쓴다.
 *
 * **매칭 후보 조회가 여기 없다.** 배치가 KO 제목 맵을 메모리에 들고 대조하므로 spot 한 행마다
 * DB를 왕복하지 않는다 — 그래서 조회용 인덱스도 저장 컬럼도 필요 없어졌다.
 *
 * upsert·`RETURNING`이 필요한 쓰기는 [SpotCatalogUpsertPort]가 따로 맡는다 — 생 SQL이라
 * 어댑터가 갈리고, **서비스가 두 포트를 각각 주입받는다**. 한쪽 어댑터가 다른 어댑터를
 * 주입받으면 어느 경로가 생 SQL을 쓰는지가 애플리케이션에서 보이지 않는다.
 */
interface SpotCatalogStorePort {

    /** 카탈로그를 저장하고 부여된 id를 돌려준다. 번역과 축제 기간도 함께 쓴다. */
    fun save(catalog: SpotCatalog): Long

    /** spot 행을 카탈로그에 배정한다. 이미 배정된 행은 덮어쓰지 않는다. */
    fun assign(spotId: Long, catalogId: Long)
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
