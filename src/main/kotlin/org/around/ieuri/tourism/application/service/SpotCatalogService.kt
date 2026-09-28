package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.CatalogAssignmentResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.CatalogLanguage
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogUpsertPort
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.event.CatalogLanguageRef
import org.around.ieuri.tourism.domain.event.SpotCatalogLanguagesChanged
import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정 유스케이스 구현(SPEC-0019). 회차 시작 때 읽은 KO 제목 키 맵에 대조해 매칭한다.
 * - KO를 먼저 전량 처리한 뒤 나머지 언어를 붙인다 — 배치 Step이 언어 순서를 지킨다.
 * - KO에 짝이 없는 외국어 spot은 자기 언어만 가진 카탈로그가 된다(한국어명을 지어내지 않는다).
 */
@Service
class SpotCatalogService(
    private val queryPort: SpotCatalogQueryPort,
    private val storePort: SpotCatalogStorePort,
    private val upsertPort: SpotCatalogUpsertPort,
    private val eventPublisher: TourismEventPublisherPort,
) : SpotCatalogUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
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
                storePort.assign(spot.spotId, matched)
                // 행이 생기는 순간 그 언어에서 해소된다.
                upsertPort.upsertTranslation(matched, spot.language, displayTitle, spot.address)
                joined++
                return@forEach
            }

            val catalogId = storePort.save(
                SpotCatalog.from(
                    language = spot.language,
                    title = displayTitle,
                    address = spot.address,
                    standard = spot.toStandardValues(),
                ),
            )
            storePort.assign(spot.spotId, catalogId)
            // 축제 기간은 spot_festival에서 온다 — 여기서 빠뜨리면 기간을 영영 갖지 못한다.
            upsertPort.refreshFestivalPeriod(catalogId)
            created++

            // KO만 자기들끼리 접는다. 비한국어는 KO에 붙거나 독립한다.
            if (language.isKorean && key != null) {
                localIndex.getOrPut(key) { mutableListOf() } +=
                    CatalogCandidate(catalogId, spot.mapX, spot.mapY, spot.thumbnailImageUrl)
            }
        }

        log.info(
            "catalog assignment: language={} joined={} created={} skipped={}",
            language,
            joined,
            created,
            skipped,
        )
        return CatalogAssignmentResult(
            joined = joined,
            catalogsCreated = created,
            skipped = skipped,
            lastSpotId = targets.last().spotId,
        )
    }

    /** 이 spot이 매칭 맵을 조회할 키. 언어를 가리지 않는다 — 제목에서 한글만 남긴다. */
    private fun UnassignedSpot.matchKey(): String? = SpotTitleNormalizer.lookupKey(title)

    /**
     * 표시용 제목. 비한국어는 한글을 걷어낸다.
     * - null이면 그 언어의 제목이 아니다 — 호출자가 그 spot을 미배정으로 남긴다.
     */
    private fun UnassignedSpot.displayTitle(): String? = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title)

    /**
     * 후보 중 이 spot의 카탈로그를 고른다. 일치가 없거나 둘 이상이면 null(새 카탈로그를 만든다).
     * - KO: 좌표와 썸네일이 모두 정확히 같아야 접는다(근사 비교 없음).
     * - 비한국어: 후보가 유일하면 좌표를 묻지 않는다.
     */
    private fun List<CatalogCandidate>.pickFor(spot: UnassignedSpot): Long? {
        if (isEmpty()) return null
        if (!spot.language.isKorean) singleOrNull()?.let { return it.catalogId }
        return singleOrNull {
            it.mapX == spot.mapX &&
                it.mapY == spot.mapY &&
                it.thumbnailImageUrl == spot.thumbnailImageUrl
        }?.catalogId
    }

    /** 카탈로그의 표준값. 좌표 유효성은 조회 포트가 이미 걸렀다. */
    private fun UnassignedSpot.toStandardValues(): CatalogStandardValues = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category,
        copyright = copyright,
    )

    /**
     * 원본 상태를 번역 행의 `visible`에 반영한다. 사라진 것은 감추고 돌아온 것은 되살린다(행은 지우지 않는다).
     * - 해소 언어의 정본을 이벤트로 알린다(SPEC-0029 R-15·R-23) — 구독자 사본의 유일한 갱신 경로다.
     */
    @Transactional
    override fun refreshCatalogVisibility() {
        val changes = storePort.refreshTranslationVisibility()

        log.info(
            "catalog visibility refreshed: hidden={} shown={} visible={}",
            changes.hiddenCount,
            changes.shownCount,
            changes.visible.size,
        )
        // 변경이 없어도 발행한다 — 델타가 아니라 정본이라, 빈 사본도 이 회차에 채워진다.
        eventPublisher.publish(
            SpotCatalogLanguagesChanged(resolved = changes.visible.map { it.toRef() }),
        )
    }

    /** 이벤트는 도메인 간 계약이라 언어를 문자열로 싣는다 — 구독자가 `DomainLocale`을 알지 않게. */
    private fun CatalogLanguage.toRef() = CatalogLanguageRef(catalogId = catalogId, languageCode = language.name)

    private companion object {
        /** 한 번의 배정(= 한 트랜잭션)에서 처리할 최대 행 수. 배치의 `CONTINUABLE`이 회차를 이어 준다. */
        const val ASSIGNMENT_BATCH_SIZE = 200
    }
}
