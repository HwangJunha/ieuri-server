package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCurationUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogAssignmentResult
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogSplitResult
import org.around.ieuri.tourism.application.catalog.provided.dto.DistrictAssignmentResult
import org.around.ieuri.tourism.application.catalog.required.CatalogEventPublisherPort
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.domain.catalog.CatalogAssignment
import org.around.ieuri.tourism.domain.catalog.CatalogCandidates
import org.around.ieuri.tourism.domain.catalog.CatalogOrigin
import org.around.ieuri.tourism.domain.catalog.CatalogSplit
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.catalog.event.SpotCatalogLanguagesChanged
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 정비(SPEC-0019·SPEC-0029·SPEC-0030 R11). 어느 카탈로그에 붙을지·어떻게 가를지는 도메인이 정한다.
 * - KO에 짝이 없는 외국어 원본은 자기 언어만 가진 카탈로그가 된다(한국어명을 지어내지 않는다).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotCatalogCurationService(
    private val catalogRepository: SpotCatalogRepository,
    private val catalogQueryPort: SpotCatalogQueryPort,
    private val eventPublisher: CatalogEventPublisherPort,
) : SpotCatalogCurationUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun assignCatalogs(language: DomainLocale, afterSpotId: Long): CatalogAssignmentResult {
        val origins = catalogQueryPort.findUnassigned(language, afterSpotId, ASSIGNMENT_BATCH_SIZE)
        if (origins.isEmpty()) return CatalogAssignmentResult(joined = 0, catalogsCreated = 0, lastSpotId = afterSpotId)

        // 썸네일 폴백은 비한국어 전용이다.
        var candidates = CatalogCandidates(
            titleCandidates = catalogQueryPort.loadKoreanTitleIndex(),
            thumbnailCatalogIds = if (language.isKorean) emptyMap() else catalogQueryPort.loadKoreanThumbnailIndex(),
        )

        var joined = 0
        var created = 0
        var skipped = 0
        origins.forEach { origin ->
            when (val assignment = candidates.assign(origin)) {
                is CatalogAssignment.Join -> {
                    join(assignment.catalogId, origin)
                    joined++
                }

                is CatalogAssignment.Found -> {
                    candidates = candidates.withFounded(origin, found(assignment.catalog) { catalogId -> catalogRepository.assign(origin.spotId, catalogId) })
                    created++
                }

                CatalogAssignment.Skip -> skipped++
            }
        }

        log.info("catalog assignment: language={} joined={} created={} skipped={}", language, joined, created, skipped)
        return CatalogAssignmentResult(joined = joined, catalogsCreated = created, skipped = skipped, lastSpotId = origins.last().spotId)
    }

    override fun splitMismatchedCatalogs(): CatalogSplitResult {
        var split = 0
        var created = 0
        var moved = 0
        catalogQueryPort.findSplitCandidates(CatalogSplit.MIN_ORIGIN_COUNT, CatalogSplit.MIN_DISTANCE_METERS).forEach { catalogId ->
            val catalog = catalogRepository.findById(catalogId) ?: return@forEach

            val result = catalog.split(catalogQueryPort.findOrigins(catalogId)) ?: return@forEach

            result.departures.forEach { departure -> found(departure.catalog) { catalogId -> catalogRepository.reassign(departure.spotIds, catalogId) } }
            catalogRepository.deleteTranslations(catalogId, result.removedLanguages)
            split++
            created += result.departures.size
            moved += result.departures.sumOf { it.spotIds.size }
        }

        log.info("catalog split: split={} created={} moved={}", split, created, moved)
        return CatalogSplitResult(catalogsSplit = split, catalogsCreated = created, spotsMoved = moved)
    }

    /** 변경이 없어도 발행한다 — 델타가 아니라 정본이라, 빈 사본도 이 회차에 채워진다(SPEC-0029 R-15·R-23). */
    override fun refreshCatalogVisibility() {
        val changes = catalogRepository.refreshTranslationVisibility()

        log.info("catalog visibility refreshed: hidden={} shown={} visible={}", changes.hiddenCount, changes.shownCount, changes.visible.size)
        // 이벤트는 도메인 간 계약이라 언어를 문자열로 싣는다 — 구독자가 `DomainLocale`을 알지 않게.
        eventPublisher.publish(SpotCatalogLanguagesChanged(resolved = changes.visible.map { CatalogLanguageRef(it.catalogId, it.language.name) }))
    }

    override fun assignDistricts(): DistrictAssignmentResult = DistrictAssignmentResult(
        assigned = catalogRepository.assignUnassignedDistricts(),
        unassigned = catalogRepository.countDistrictUnassigned(),
    )

    /** 기존 카탈로그에 원본을 붙인다. 번역이 바뀔 때만 쓴다. */
    private fun join(catalogId: Long, origin: CatalogOrigin) {
        val catalog = requireNotNull(catalogRepository.findById(catalogId)) { "배정 후보 카탈로그가 없다: $catalogId" }

        catalog.attach(origin)?.let { catalogRepository.saveTranslation(catalog, it) }

        catalogRepository.assign(origin.spotId, catalogId)
    }

    /**
     * 새 카탈로그를 저장하고 원본을 붙인 뒤 축제 기간을 정한다 — 원본이 붙은 뒤라야 기간을 고를 수 있다.
     * - 배정은 미배정 원본만 붙이고(`assign`), 분할은 기존 배정을 옮긴다(`reassign`).
     */
    private fun found(catalog: SpotCatalog, attachOrigins: (Long) -> Unit): Long {
        val catalogId = catalogRepository.insert(catalog)
        attachOrigins(catalogId)
        catalogRepository.saveFestivalPeriod(catalogId, SpotCatalog.festivalPeriodOf(catalogRepository.findOriginFestivalPeriods(catalogId)))
        return catalogId
    }

    private companion object {
        /** 한 번의 배정(= 한 회차)에서 처리할 최대 행 수. 배치의 `CONTINUABLE`이 회차를 이어 준다. */
        const val ASSIGNMENT_BATCH_SIZE = 200
    }
}
