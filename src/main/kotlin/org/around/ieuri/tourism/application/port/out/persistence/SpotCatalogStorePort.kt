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

    /**
     * 원본 생존 여부를 번역 행의 `visible`에 반영하고, **반영 후 노출 중인 것 전부**를 돌려준다.
     *
     * 상류에서 장소가 없어지면 `spot` 행은 지워지지만 카탈로그와 번역은 남는다. 그대로 두면
     * 목록에는 나오는데 상세를 열면 실패하는 장소가 되므로, 지우지 않고 감춘다 — 번역 행을
     * 지우면 배정 배치가 그 카탈로그를 못 찾아 좋아요가 달린 옛 카탈로그가 고아가 된다.
     *
     * **돌려주는 것은 변경분이 아니라 현재 상태 전부다.** 구독자가 이 값으로 자기 사본을
     * 통째로 맞춘다 — 변경분만 주면 빈 사본을 채울 수 없다(전량 재적재 후에는 뒤집힌 행이 없다).
     */
    fun refreshTranslationVisibility(): VisibilityRefreshResult
}

/** 노출 여부가 바뀐 (카탈로그, 언어). 다른 도메인이 자기 사본을 맞추는 근거다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: DomainLocale,
)

/**
 * 노출 갱신의 결과.
 *
 * [visible]이 **구독자가 받는 정본**이다 — 갱신 후 노출 중인 (카탈로그, 언어) 전부.
 * [hiddenCount]·[shownCount]는 이번 회차에 뒤집힌 행 수이며 **로그·관측용일 뿐**이라
 * 어떤 행인지는 담지 않는다. 그것까지 알아내려면 `UPDATE ... RETURNING`이 필요한데,
 * 정본을 그대로 싣는 이상 쓸 데가 없다.
 */
data class VisibilityRefreshResult(
    val hiddenCount: Int,
    val shownCount: Int,
    val visible: List<CatalogLanguage>,
) {
    val changedCount: Int get() = hiddenCount + shownCount
}
