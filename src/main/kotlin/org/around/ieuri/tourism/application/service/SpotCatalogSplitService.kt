package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.CatalogSplitResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogSplitUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogSplitPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.application.port.out.query.CatalogOrigin
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogSpatialQueryPort
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.specification.OriginProximityClustering
import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 잘못 접힌 카탈로그 분할(SPEC-0029).
 * - 후보는 원본 수 초과와 원본 간 거리 조건의 AND로 고른다.
 */
@Service
class SpotCatalogSplitService(
    private val spatialQueryPort: SpotCatalogSpatialQueryPort,
    private val storePort: SpotCatalogStorePort,
    private val splitPort: SpotCatalogSplitPort,
    private val upsertPort: SpotCatalogUpsertPort,
) : SpotCatalogSplitUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun splitMismatchedCatalogs(): CatalogSplitResult {
        val candidates = spatialQueryPort.findSplitCandidates(MIN_ORIGIN_COUNT, MIN_DISTANCE_METERS)
        if (candidates.isEmpty()) return CatalogSplitResult(0, 0, 0)

        var split = 0
        var created = 0
        var moved = 0

        candidates.forEach { catalogId ->
            val outcome = splitOne(catalogId)
            if (outcome.catalogsCreated > 0) {
                split++
                created += outcome.catalogsCreated
                moved += outcome.spotsMoved
            }
        }

        log.info("catalog split: split={} created={} moved={}", split, created, moved)
        return CatalogSplitResult(catalogsSplit = split, catalogsCreated = created, spotsMoved = moved)
    }

    /** 카탈로그 하나를 가른다. 군집이 하나면 아무것도 하지 않는다. */
    private fun splitOne(catalogId: Long): CatalogSplitResult {
        val origins = spatialQueryPort.findOrigins(catalogId)
        val clusters = OriginProximityClustering.cluster(origins) { it.mapX to it.mapY }
        if (clusters.size < 2) return CatalogSplitResult(0, 0, 0)

        // 가장 큰 군집이 기존 id를 지킨다. 동수면 앞선 군집이 남는다.
        val stays = clusters.maxBy { it.size }
        val leaving = clusters.filter { it !== stays }

        var created = 0
        var moved = 0
        leaving.forEach { cluster ->
            val newCatalogId = createCatalogFor(cluster)
            splitPort.reassign(cluster.map { it.spotId }, newCatalogId)
            created++
            moved += cluster.size
        }

        // 남는 군집에 없는 언어의 번역 행을 지운다.
        val stayingLanguages = stays.map { it.language }.toSet()
        val departed = leaving.flatMap { cluster -> cluster.map { it.language } }
            .distinct()
            .filterNot { it in stayingLanguages }
        if (departed.isNotEmpty()) splitPort.deleteTranslations(catalogId, departed)

        log.info(
            "catalog {} split into {} places: staying={} leaving={} translationsRemoved={}",
            catalogId,
            clusters.size,
            stays.size,
            moved,
            departed.size,
        )
        return CatalogSplitResult(catalogsSplit = 1, catalogsCreated = created, spotsMoved = moved)
    }

    /**
     * 군집 하나로 새 카탈로그를 만들고 그 언어들의 번역과 축제 기간을 심는다.
     * - 표준값은 [STANDARD_VALUE_PRIORITY]의 첫 원본에서 온다.
     */
    private fun createCatalogFor(cluster: List<CatalogOrigin>): Long {
        val representative = cluster.minBy { STANDARD_VALUE_PRIORITY.indexOf(it.language) }
        val newCatalogId = storePort.save(
            SpotCatalog.from(
                language = representative.language,
                title = representative.displayTitle(),
                address = representative.address,
                standard = representative.toStandardValues(),
            ),
        )
        upsertPort.refreshFestivalPeriod(newCatalogId)

        // 대표의 번역은 save가 이미 썼다. 같은 언어가 둘이면 upsert가 뒤엣것을 무시한다.
        cluster.filter { it !== representative }.forEach { origin ->
            upsertPort.upsertTranslation(newCatalogId, origin.language, origin.displayTitle(), origin.address)
        }
        return newCatalogId
    }

    /** 표시용 제목. 비한국어의 병기 한국어를 걷어내며, 정규화가 빈 값을 내면 원본 제목을 쓴다. */
    private fun CatalogOrigin.displayTitle(): String = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title) ?: title

    private fun CatalogOrigin.toStandardValues(): CatalogStandardValues = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category,
        copyright = copyright,
    )

    private companion object {
        /** 이 수를 초과하는 원본을 가진 카탈로그만 후보다. */
        const val MIN_ORIGIN_COUNT = 4

        /** 원본 간 최대 거리가 이 값 **이상**이면 다른 장소로 본다. */
        const val MIN_DISTANCE_METERS = 10_000.0

        /** 표준값을 내줄 대표를 고르는 순서. `SpotCatalog.from`과 같다. */
        val STANDARD_VALUE_PRIORITY =
            listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}
