package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.CatalogLanguage
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotCatalogTranslationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper.toNewTranslationEntities
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotCatalogJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.SpotCatalogTranslationJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 쓰기 포트 구현(SPEC-0019). 도메인↔엔티티 변환은 `mapper` 패키지가 담당한다.
 *
 * **여기에는 JPA·QueryDSL로 표현되는 쓰기만 있다.** upsert는 생 SQL이 필요해
 * `SpotCatalogUpsertJdbcAdapter`가 맡으며, 서비스가 그 포트를 따로 주입받는다 —
 * 이 어댑터가 저쪽을 주입받으면 어느 경로가 생 SQL을 쓰는지가 가려진다.
 */
@Component
class SpotCatalogStoreAdapter(
    private val queryFactory: JPAQueryFactory,
    private val catalogRepository: SpotCatalogJpaRepository,
    private val translationRepository: SpotCatalogTranslationJpaRepository,
) : SpotCatalogStorePort {

    private val spot = QSpotEntity.spotEntity
    private val translation = QSpotCatalogTranslationEntity.spotCatalogTranslationEntity

    /**
     * 카탈로그와 번역을 함께 저장한다.
     *
     * **부모를 먼저 flush해야 한다** — 자식이 들고 갈 `catalog_id`가 그때 정해진다.
     */
    @Transactional
    override fun save(catalog: SpotCatalog): Long {
        val saved = catalogRepository.saveAndFlush(catalog.toNewEntity())
        val catalogId = requireNotNull(saved.id) { "저장 직후 카탈로그 id가 없다." }

        translationRepository.saveAll(catalog.toNewTranslationEntities(catalogId))
        return catalogId
    }

    /**
     * spot을 카탈로그에 배정한다. **이미 배정된 행은 덮어쓰지 않는다** — 재실행이 기존 배정을
     * 흔들면 좋아요가 딸려 다른 장소로 옮겨 간다.
     *
     * 벌크 업데이트라 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는
     * 코드가 없으므로 안전하다.
     */
    @Transactional
    override fun assign(spotId: Long, catalogId: Long) {
        queryFactory
            .update(spot)
            .set(spot.catalogId, catalogId)
            .where(spot.id.eq(spotId), spot.catalogId.isNull)
            .execute()
    }

    /**
     * 원본 생존 여부를 번역 행의 `visible`에 반영하고, **반영 후 노출 중인 것 전부**를 돌려준다.
     *
     * `visible`은 **그 언어에 원본이 있는가**다(SPEC-0029). 상류에서 장소가 없어지면 동기화가
     * `spot` 행을 지우지만 카탈로그와 번역은 남는데, 그대로 두면 목록에는 나오는데 상세를 열면
     * 실패하는 장소가 된다. **지우지 않고 감추는 이유는** 번역 행을 지우면 배정 배치의 KO 제목
     * 맵이 그 카탈로그를 못 찾아, 장소가 돌아왔을 때 좋아요가 달린 옛 카탈로그 대신 새 카탈로그가
     * 생기기 때문이다. 그래서 양방향이다 — 사라진 것은 감추고 돌아온 것은 되살린다.
     *
     * **돌려주는 것은 변경분이 아니라 현재 상태 전부다.** 구독자(커뮤니티의 언어 사본)가 빈
     * 상태에서 출발하기 때문이다 — 전량 재적재 후에는 모든 행이 신규 INSERT라 `visible`이
     * 뒤집힌 적이 없고, 변경분만 주면 사본이 영원히 채워지지 않는다.
     *
     * **갱신 뒤에 새로 읽으므로** 두 UPDATE 사이에 동시 upsert가 끼어들었더라도 그 결과까지
     * 반영된 최신 상태가 나온다.
     *
     * 벌크 갱신이라 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는 코드가
     * 없으므로 안전하다. **상태가 실제로 바뀐 행만 건드린다** — 매 실행마다 전 행을 갱신하면
     * 그 UPDATE가 전부 복제로 흘러가 검색 노드가 불필요하게 출렁인다.
     */
    @Transactional
    override fun refreshTranslationVisibility(): VisibilityRefreshResult {
        // **그 언어의** 원본이 있는가. 이것이 곧 노출 여부다.
        val originExists = JPAExpressions.selectOne().from(spot)
            .where(spot.catalogId.eq(translation.catalogId), spot.language.eq(translation.language))
            .exists()

        val hidden = queryFactory
            .update(translation)
            .set(translation.visible, false)
            .where(translation.visible.isTrue, originExists.not())
            .execute()

        val shown = queryFactory
            .update(translation)
            .set(translation.visible, true)
            .where(translation.visible.isFalse, originExists)
            .execute()

        return VisibilityRefreshResult(
            hiddenCount = hidden.toInt(),
            shownCount = shown.toInt(),
            visible = findVisible(),
        )
    }

    /** 지금 노출 중인 (카탈로그, 언어) 전부. 구독자가 사본을 맞추는 정본이다. */
    private fun findVisible(): List<CatalogLanguage> = queryFactory
        .select(translation.catalogId, translation.language)
        .from(translation)
        .where(translation.visible.isTrue)
        .fetch()
        .map {
            CatalogLanguage(
                catalogId = requireNotNull(it.get(translation.catalogId)),
                language = requireNotNull(it.get(translation.language)),
            )
        }
}
