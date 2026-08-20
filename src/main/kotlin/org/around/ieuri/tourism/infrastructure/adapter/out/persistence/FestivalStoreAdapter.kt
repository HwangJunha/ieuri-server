package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.FestivalStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotFestivalEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 축제 정보 조회·정리 포트 구현. QueryDSL로 표현되는 것만 여기 있고, upsert는 생 SQL이라
 * `FestivalUpsertPort`(→ [FestivalUpsertJdbcAdapter])로 갈라져 있다.
 *
 * 삭제는 전부 `spot`을 대상으로 한다 — `spot_festival` 행은 FK CASCADE로 따라간다.
 */
@Component
class FestivalStoreAdapter(
    private val queryFactory: JPAQueryFactory,
) : FestivalStorePort {

    private val spot = QSpotEntity.spotEntity
    private val festival = QSpotFestivalEntity.spotFestivalEntity

    /**
     * 종료된 축제 삭제. `event_end_date < asOf` — 종료일 당일은 아직 유효하다.
     * 도메인 `FestivalPeriod.isEndedAsOf`와 같은 부등호여야 한다.
     */
    @Transactional
    override fun deleteEnded(language: DomainLocale, asOf: LocalDate): Int =
        // [findEndedContentIds]와 **같은 헬퍼**로 대상을 뽑는다 — 조건이 갈리면 제외 집합과
        // 실제 삭제 대상이 어긋난다. 애노테이션이 붙은 메서드를 자기호출하면 프록시를 우회하므로
        // 조건을 공용 헬퍼로 두고 양쪽이 그것을 호출한다.
        deleteSpotsInChunks(language, endedContentIds(language, asOf).toList())

    /**
     * 이번 수집 결과에 없는 축제 삭제. `spot_festival`에 있는 행만 대상으로 한다 —
     * 관광지 전반을 건드리면 축제 정리가 관광지 정리를 덮어쓴다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 그 언어의 축제가 전량 삭제된다. 게이트가 막아야 하지만 여기서도 한 번 더 막는다.
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = queryFactory
            .select(spot.contentId)
            .from(festival)
            .join(spot).on(spot.id.eq(festival.spotId))
            .where(spot.language.eq(language))
            .fetch()
            .filterNot { it in survivingContentIds }

        return deleteSpotsInChunks(language, doomed)
    }

    @Transactional(readOnly = true)
    override fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String> =
        endedContentIds(language, asOf)

    /** [asOf] 기준 종료된 축제의 contentId. 조회와 삭제가 같은 조건을 쓰도록 한 곳에 둔다. */
    private fun endedContentIds(language: DomainLocale, asOf: LocalDate): Set<String> =
        queryFactory
            .select(spot.contentId)
            .from(festival)
            .join(spot).on(spot.id.eq(festival.spotId))
            .where(
                spot.language.eq(language),
                festival.eventEndDate.lt(asOf),
            )
            .fetch()
            .toSet()

    /**
     * [contentIds]에 해당하는 `spot` 행을 [DELETE_CHUNK_SIZE]씩 나눠 지운다. `spot_festival`은
     * FK CASCADE로 따라 사라진다.
     *
     * 한 트랜잭션 안에서 여러 번 삭제하므로 중간 실패 시 전체가 롤백된다 — 청크는 파라미터 한도를
     * 넘지 않기 위한 분할이지 커밋 경계가 아니다.
     */
    private fun deleteSpotsInChunks(language: DomainLocale, contentIds: List<String>): Int {
        if (contentIds.isEmpty()) return 0
        return contentIds.chunked(DELETE_CHUNK_SIZE).sumOf { chunk ->
            queryFactory
                .delete(spot)
                .where(spot.language.eq(language), spot.contentId.`in`(chunk))
                .execute()
                .toInt()
        }
    }

    private companion object {
        /** 삭제 `IN` 절 하나에 넣을 최대 id 수. 드라이버 파라미터 한도를 넘지 않기 위한 분할 단위다. */
        const val DELETE_CHUNK_SIZE = 1_000
    }
}
