package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 관광지 목록 정리 포트 구현. QueryDSL로 표현되는 삭제만 여기 있고, upsert는 생 SQL이라
 * `SpotUpsertPort`(→ [SpotUpsertJdbcAdapter])로 갈라져 있다 — 호출자가 두 포트를 함께 주입받는다.
 */
@Component
class SpotStoreAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotStorePort {

    private val spot = QSpotEntity.spotEntity

    /**
     * 이번 수집 결과에 없는 항목 삭제.
     *
     * 여집합을 SQL이 아니라 메모리에서 계산한다 — `NOT IN`을 청크로 쪼개면 각 청크가 "그 청크에
     * 없는 전부"를 지워 첫 청크에서 데이터가 날아간다. 삭제 대상 목록은 그냥 목록이라 나눌 수 있다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 전량 삭제가 된다. 게이트가 막아야 하지만 여기서도 한 번 더 막는다.
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        val doomed = queryFactory
            .select(spot.contentId)
            .from(spot)
            .where(spot.language.eq(language))
            .fetch()
            .filterNot { it in survivingContentIds }

        return deleteByContentIdsInChunks(language, doomed)
    }

    /**
     * [contentIds]를 [DELETE_CHUNK_SIZE]씩 나눠 삭제하고 총 삭제 행 수를 돌려준다.
     *
     * 한 트랜잭션 안에서 여러 번 삭제하므로 중간에 실패하면 전체가 롤백된다 — 청크는 파라미터
     * 한도를 넘지 않기 위한 분할이지 커밋 경계가 아니다.
     */
    private fun deleteByContentIdsInChunks(language: DomainLocale, contentIds: List<String>): Int {
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
