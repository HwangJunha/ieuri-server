package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.tourism.application.port.out.persistence.SeedLoadResult
import org.around.ieuri.tourism.application.port.out.persistence.SeededTranslation
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogSeedPort
import org.around.ieuri.tourism.domain.vo.TranslationSource
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.QSpotEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 초기 번역 시드 적재 어댑터(SPEC-0019 §5.4).
 *
 * **시드와 DB가 어긋나면 적재하지 않고 로그에 남긴다** — `ko_spot_ids`로 찾은 행의 제목·좌표가
 * 시드와 다르면 그 사이 TourAPI가 데이터를 바꾼 것이다. 조용히 덮으면 다른 장소의 제목이
 * 붙는다.
 */
@Component
class SpotCatalogSeedAdapter(
    private val queryFactory: JPAQueryFactory,
    private val upsertAdapter: SpotCatalogUpsertJdbcAdapter,
) : SpotCatalogSeedPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val spot = QSpotEntity.spotEntity

    @Transactional
    override fun loadSeed(entries: List<SeededTranslation>): SeedLoadResult {
        if (entries.isEmpty()) return SeedLoadResult(loaded = 0, mismatched = 0)

        // 한 묶음의 KO 행을 한 번에 읽는다 — 항목마다 조회하면 500번 왕복한다.
        val rows = queryFactory
            .select(spot.id, spot.title, spot.mapX, spot.mapY, spot.catalogId)
            .from(spot)
            .where(spot.id.`in`(entries.flatMap { it.koSpotIds }))
            .fetch()
            .associate { requireNotNull(it.get(spot.id)) to KoRow(it.get(spot.title), it.get(spot.mapX), it.get(spot.mapY), it.get(spot.catalogId)) }

        var loaded = 0
        var mismatched = 0

        entries.forEach { entry ->
            // 어느 행이든 같은 카탈로그에 속하므로 검증을 통과하는 첫 행을 쓴다.
            val matched = entry.koSpotIds.firstNotNullOfOrNull { spotId ->
                rows[spotId]?.takeIf { entry.matches(it) }?.catalogId
            }

            if (matched == null) {
                mismatched++
                log.debug("translation seed mismatched: koSpotIds={} title={}", entry.koSpotIds, entry.expectedKoreanTitle)
                return@forEach
            }

            entry.cards.forEach { (language, card) ->
                // MACHINE이라 이미 제목이 있으면 덮지 않는다 — 공식 표기가 우선한다(R-4).
                upsertAdapter.upsertTranslation(
                    matched, language, card.title, card.address, TranslationSource.MACHINE,
                )
                loaded++
            }
        }

        return SeedLoadResult(loaded = loaded, mismatched = mismatched)
    }

    /**
     * 시드가 이 행을 가리키는 것이 맞는가. 제목과 좌표를 **그대로 등치 비교한다** —
     * 반올림하면 좌표가 갱신된 행을 통과시킨다.
     *
     * 카탈로그가 없는 행은 아직 배정되지 않은 것이므로 통과시키지 않는다.
     */
    private fun SeededTranslation.matches(row: KoRow): Boolean =
        row.catalogId != null &&
            row.title == expectedKoreanTitle &&
            row.mapX == expectedMapX &&
            row.mapY == expectedMapY

    /** 검증에 필요한 KO 행의 값만. */
    private data class KoRow(
        val title: String?,
        val mapX: Double?,
        val mapY: Double?,
        val catalogId: Long?,
    )
}
