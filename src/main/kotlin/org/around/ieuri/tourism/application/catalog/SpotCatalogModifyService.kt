package org.around.ieuri.tourism.application.catalog

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogCommandUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogAssignmentResult
import org.around.ieuri.tourism.application.catalog.provided.dto.CatalogSplitResult
import org.around.ieuri.tourism.application.catalog.required.CatalogCandidate
import org.around.ieuri.tourism.application.catalog.required.CatalogLanguage
import org.around.ieuri.tourism.application.catalog.required.CatalogOrigin
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogSpatialQueryPort
import org.around.ieuri.tourism.application.catalog.required.UnassignedSpot
import org.around.ieuri.tourism.application.shared.required.TourismEventPublisherPort
import org.around.ieuri.tourism.domain.catalog.CatalogStandardValues
import org.around.ieuri.tourism.domain.catalog.OriginProximityClustering
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.catalog.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.catalog.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.catalog.event.SpotCatalogLanguagesChanged
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정·분할·노출 갱신(SPEC-0019·SPEC-0029). 배치 스텝 트랜잭션 안에서 돈다(D-71).
 * - 배정은 회차 시작 때 읽은 KO 제목 키 맵에 대조한다. KO를 먼저 전량 처리한 뒤 나머지 언어를 붙인다 — 배치 Step이 언어 순서를 지킨다.
 * - KO에 짝이 없는 외국어 spot은 자기 언어만 가진 카탈로그가 된다(한국어명을 지어내지 않는다).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class SpotCatalogModifyService(
    private val catalogRepository: SpotCatalogRepository,
    private val queryPort: SpotCatalogQueryPort,
    private val spatialQueryPort: SpotCatalogSpatialQueryPort,
    private val eventPublisher: TourismEventPublisherPort,
) : SpotCatalogCommandUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun assignCatalogs(language: DomainLocale, afterSpotId: Long): CatalogAssignmentResult {
        val targets = queryPort.findUnassigned(language, afterSpotId, ASSIGNMENT_BATCH_SIZE)
        if (targets.isEmpty()) return CatalogAssignmentResult(joined = 0, catalogsCreated = 0, lastSpotId = afterSpotId)

        // KO도 이 맵을 읽는다 — 회차 경계를 넘는 같은 장소는 localIndex만으로 못 접는다.
        val koreanIndex = queryPort.loadKoreanTitleIndex()
        // 썸네일 폴백은 비한국어 전용이다.
        val thumbnailIndex = if (language.isKorean) emptyMap() else queryPort.loadKoreanThumbnailIndex()

        // 이 회차에서 만든 카탈로그도 다음 행의 후보다.
        val localIndex = mutableMapOf<String, MutableList<CatalogCandidate>>()

        var joined = 0
        var created = 0
        var skipped = 0

        targets.forEach { spot ->
            // 표시할 제목이 없으면 미배정으로 남긴다 — 상류가 제대로 된 제목을 주는 날 배정된다.
            val displayTitle = spot.displayTitle()
            if (displayTitle == null) {
                skipped++
                return@forEach
            }

            val key = spot.matchKey()
            val candidates = key?.let { koreanIndex[it].orEmpty() + localIndex[it].orEmpty() }.orEmpty()

            // 제목으로 못 찾으면 썸네일로 내려간다.
            val matched = candidates.pickFor(spot) ?: thumbnailIndex[spot.thumbnailImageUrl]
            if (matched != null) {
                catalogRepository.assign(spot.spotId, matched)
                // 행이 생기는 순간 그 언어에서 해소된다.
                catalogRepository.upsertTranslation(matched, spot.language, displayTitle, spot.address)
                joined++
                return@forEach
            }

            val catalogId = catalogRepository.save(
                SpotCatalog.from(language = spot.language, title = displayTitle, address = spot.address, standard = spot.toStandardValues()),
            )
            catalogRepository.assign(spot.spotId, catalogId)
            // 축제 기간은 spot_festival에서 온다 — 여기서 빠뜨리면 기간을 영영 갖지 못한다.
            catalogRepository.refreshFestivalPeriod(catalogId)
            created++

            // KO만 자기들끼리 접는다. 비한국어는 KO에 붙거나 독립한다.
            if (language.isKorean && key != null) {
                localIndex.getOrPut(key) { mutableListOf() } += CatalogCandidate(catalogId, spot.mapX, spot.mapY, spot.thumbnailImageUrl)
            }
        }

        log.info("catalog assignment: language={} joined={} created={} skipped={}", language, joined, created, skipped)
        return CatalogAssignmentResult(joined = joined, catalogsCreated = created, skipped = skipped, lastSpotId = targets.last().spotId)
    }

    /** 후보는 원본 수 초과와 원본 간 거리 조건의 AND로 고른다. */
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

    /**
     * 원본 상태를 번역 행의 `visible`에 반영한다. 사라진 것은 감추고 돌아온 것은 되살린다(행은 지우지 않는다).
     * - 해소 언어의 정본을 이벤트로 알린다(SPEC-0029 R-15·R-23) — 구독자 사본의 유일한 갱신 경로다.
     */
    override fun refreshCatalogVisibility() {
        val changes = catalogRepository.refreshTranslationVisibility()

        log.info(
            "catalog visibility refreshed: hidden={} shown={} visible={}",
            changes.hiddenCount,
            changes.shownCount,
            changes.visible.size,
        )
        // 변경이 없어도 발행한다 — 델타가 아니라 정본이라, 빈 사본도 이 회차에 채워진다.
        eventPublisher.publish(SpotCatalogLanguagesChanged(resolved = changes.visible.map { it.toRef() }))
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
            catalogRepository.reassign(cluster.map { it.spotId }, newCatalogId)
            created++
            moved += cluster.size
        }

        // 남는 군집에 없는 언어의 번역 행을 지운다.
        val stayingLanguages = stays.map { it.language }.toSet()
        val departed = leaving.flatMap { cluster -> cluster.map { it.language } }
            .distinct()
            .filterNot { it in stayingLanguages }
        if (departed.isNotEmpty()) catalogRepository.deleteTranslations(catalogId, departed)

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
        val newCatalogId = catalogRepository.save(
            SpotCatalog.from(
                language = representative.language,
                title = representative.displayTitle(),
                address = representative.address,
                standard = representative.toStandardValues(),
            ),
        )
        catalogRepository.refreshFestivalPeriod(newCatalogId)

        // 대표의 번역은 save가 이미 썼다. 같은 언어가 둘이면 upsert가 뒤엣것을 무시한다.
        cluster.filter { it !== representative }.forEach { origin ->
            catalogRepository.upsertTranslation(newCatalogId, origin.language, origin.displayTitle(), origin.address)
        }
        return newCatalogId
    }

    /** 이 spot이 매칭 맵을 조회할 키. 언어를 가리지 않는다 — 제목에서 한글만 남긴다. */
    private fun UnassignedSpot.matchKey(): String? = SpotTitleNormalizer.lookupKey(title)

    /** 표시용 제목. 비한국어는 한글을 걷어낸다. null이면 그 언어의 제목이 아니다 — 호출자가 미배정으로 남긴다. */
    private fun UnassignedSpot.displayTitle(): String? = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title)

    /**
     * 후보 중 이 spot의 카탈로그를 고른다. 일치가 없거나 둘 이상이면 null(새 카탈로그를 만든다).
     * - KO: 좌표와 썸네일이 모두 정확히 같아야 접는다(근사 비교 없음).
     * - 비한국어: 후보가 유일하면 좌표를 묻지 않는다.
     */
    private fun List<CatalogCandidate>.pickFor(spot: UnassignedSpot): Long? {
        if (isEmpty()) return null
        if (!spot.language.isKorean) singleOrNull()?.let { return it.catalogId }
        return singleOrNull { it.mapX == spot.mapX && it.mapY == spot.mapY && it.thumbnailImageUrl == spot.thumbnailImageUrl }?.catalogId
    }

    /** 카탈로그의 표준값. 좌표 유효성은 조회 포트가 이미 걸렀다. */
    private fun UnassignedSpot.toStandardValues(): CatalogStandardValues = CatalogStandardValues(mapX, mapY, thumbnailImageUrl, category, copyright)

    /** 표시용 제목. 비한국어의 병기 한국어를 걷어내며, 정규화가 빈 값을 내면 원본 제목을 쓴다. */
    private fun CatalogOrigin.displayTitle(): String = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title) ?: title

    private fun CatalogOrigin.toStandardValues(): CatalogStandardValues = CatalogStandardValues(mapX, mapY, thumbnailImageUrl, category, copyright)

    /** 이벤트는 도메인 간 계약이라 언어를 문자열로 싣는다 — 구독자가 `DomainLocale`을 알지 않게. */
    private fun CatalogLanguage.toRef() = CatalogLanguageRef(catalogId = catalogId, languageCode = language.name)

    private companion object {
        /** 한 번의 배정(= 한 회차)에서 처리할 최대 행 수. 배치의 `CONTINUABLE`이 회차를 이어 준다. */
        const val ASSIGNMENT_BATCH_SIZE = 200

        /** 이 수를 초과하는 원본을 가진 카탈로그만 분할 후보다. */
        const val MIN_ORIGIN_COUNT = 4

        /** 원본 간 최대 거리가 이 값 **이상**이면 다른 장소로 본다. */
        const val MIN_DISTANCE_METERS = 10_000.0

        /** 표준값을 내줄 대표를 고르는 순서. */
        val STANDARD_VALUE_PRIORITY = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}
