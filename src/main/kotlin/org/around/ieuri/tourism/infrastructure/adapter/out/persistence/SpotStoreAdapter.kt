package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 관광지 목록 정리 포트 구현. upsert는 [SpotUpsertJdbcAdapter]가 따로 맡는다. */
@Component
class SpotStoreAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotStorePort {

    private val spot = QSpotEntity.spotEntity

    /**
     * 이번 수집 결과에 없는 항목 삭제.
     * - 여집합은 메모리에서 계산한다 — `NOT IN`을 청크로 쪼개면 첫 청크가 데이터를 날린다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합이면 전량 삭제된다 — 게이트와 별개로 여기서도 막는다.
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
     * - 청크는 파라미터 한도 때문의 분할이지 커밋 경계가 아니다 — 중간 실패 시 전체가 롤백된다.
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
        /** 삭제 `IN` 절 하나에 넣을 최대 id 수(드라이버 파라미터 한도). */
        const val DELETE_CHUNK_SIZE = 1_000
    }
}
