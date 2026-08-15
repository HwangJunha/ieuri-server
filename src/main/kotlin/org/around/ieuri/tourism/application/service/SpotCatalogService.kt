package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.CatalogAssignmentResult
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.`in`.command.TranslationResult
import org.around.ieuri.tourism.application.event.TranslationTarget
import org.around.ieuri.tourism.application.event.TranslationTargetsFound
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogStorePort
import org.around.ieuri.tourism.application.port.out.query.CatalogCandidate
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.application.port.out.query.UnassignedSpot
import org.around.ieuri.tourism.domain.aggregate.CatalogStandardValues
import org.around.ieuri.tourism.domain.aggregate.SpotCatalog
import org.around.ieuri.tourism.domain.specification.SpotTitleNormalizer
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카탈로그 배정·번역 유스케이스 구현(SPEC-0019).
 *
 * **KO를 먼저 전량 처리해 카탈로그를 만든 뒤, 나머지 언어를 그 위에 붙인다.** 배치 Step이
 * 언어 순서를 지키며 이 서비스를 호출한다.
 *
 * 매칭은 **메모리 맵 대조**다. KO 제목 키 → 카탈로그 맵을 회차 시작 때 한 번 읽고, 비한국어
 * spot이 제목 괄호에서 뽑은 한국어로 그 맵을 조회한다. spot 한 행마다 DB를 왕복하지 않으므로
 * 매칭용 인덱스도 저장 컬럼도 필요 없다.
 *
 * KO에 짝이 없는 외국어 spot은 **자기 언어만 가진 카탈로그**가 된다. 한국어명을 지어내지
 * 않는다 — 원본에 없는 이름을 만들면 정합성이 깨진다.
 */
@Service
class SpotCatalogService(
    private val queryPort: SpotCatalogQueryPort,
    private val storePort: SpotCatalogStorePort,
    private val eventPublisher: TourismEventPublisherPort,
) : SpotCatalogUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun assignCatalogs(language: DomainLocale, afterSpotId: Long): CatalogAssignmentResult {
        val targets = queryPort.findUnassigned(language, afterSpotId, ASSIGNMENT_BATCH_SIZE)
        if (targets.isEmpty()) return CatalogAssignmentResult(joined = 0, catalogsCreated = 0, lastSpotId = afterSpotId)

        // **KO도 이 맵을 읽는다.** 회차가 200건씩 끊기므로 경계를 사이에 둔 같은 장소는
        // localIndex(회차 한정)로는 못 접고 카탈로그가 둘로 갈린다.
        val koreanIndex = queryPort.loadKoreanTitleIndex()
        // 썸네일 폴백은 비한국어 전용이다 — KO끼리는 제목과 좌표로 충분하고, 썸네일로 접으면
        // 같은 사진을 쓰는 다른 장소가 뭉친다.
        val thumbnailIndex = if (language.isKorean) emptyMap() else queryPort.loadKoreanThumbnailIndex()

        // **이 회차에서 만든 카탈로그도 다음 행의 후보다.** 같은 배치 안에 같은 장소가 여러 번
        // 나오면(KO 자체 중복, 같은 이름의 외국어 행) 맵에 없으므로 여기 모아 함께 본다.
        val localIndex = mutableMapOf<String, MutableList<CatalogCandidate>>()

        var joined = 0
        var created = 0

        var skipped = 0

        targets.forEach { spot ->
            // **표시할 제목이 없으면 배정하지 않는다.** TourAPI가 그 언어 제목 자리에 한국어를
            // 넣은 경우이며(`초려공방`), 저장하면 영어 사용자가 한글을 보게 되고 행이 있으니
            // 번역 대상에서도 빠져 영영 고쳐지지 않는다. 미배정으로 남겨 두면 상류가 제대로 된
            // 제목을 주는 날 자연히 배정된다.
            val displayTitle = spot.displayTitle()
            if (displayTitle == null) {
                skipped++
                return@forEach
            }

            val key = spot.matchKey()
            val candidates = key?.let { koreanIndex[it].orEmpty() + localIndex[it].orEmpty() }.orEmpty()

            // 제목으로 못 찾으면 썸네일로 내려간다. **TourAPI가 같은 장소의 언어별 제목을
            // 다르게 주는 경우가 실재한다** — `한화리조트 용인 베잔송`(KO)과
            // `한화리조트 / 용인`(ZH)은 좌표·썸네일이 같은데 이름만 다르다. 그때 남는 신호가
            // 썸네일이며, KO가 유일한 URL만 담긴 맵이라 오판 위험이 낮다(카테고리 일치율 97%).
            val matched = candidates.pickFor(spot) ?: thumbnailIndex[spot.thumbnailImageUrl]
            if (matched != null) {
                storePort.assign(spot.spotId, matched)
                // 이 언어의 값은 TourAPI 원문이다 — 번역 대상이 되지 않게 지금 심는다.
                storePort.upsertTranslation(matched, spot.language, displayTitle, spot.address)
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
            // **축제 기간은 저장한 행이 아니라 spot_festival에서 온다.** 여기서 빠뜨리면
            // 그 축제는 기간을 영영 갖지 못한다.
            storePort.refreshFestivalPeriod(catalogId)
            created++

            // KO만 자기들끼리 접는다. 비한국어는 KO에 붙거나 독립하며, 서로 뭉치면 한국어
            // 이름이 없는 카탈로그가 여러 언어를 갖게 되어 어느 것이 대표인지 정할 수 없다.
            if (language.isKorean && key != null) {
                localIndex.getOrPut(key) { mutableListOf() } +=
                    CatalogCandidate(catalogId, spot.mapX, spot.mapY)
            }
        }

        log.info(
            "catalog assignment: language={} joined={} created={} skipped={}",
            language, joined, created, skipped,
        )
        return CatalogAssignmentResult(
            joined = joined,
            catalogsCreated = created,
            skipped = skipped,
            lastSpotId = targets.last().spotId,
        )
    }

    /**
     * 이 spot이 매칭 맵을 조회할 키. **언어를 가리지 않는다** — 제목에서 한글만 남기므로
     * `Marine City (마린시티)`와 `마린시티`가 같은 키가 된다.
     */
    private fun UnassignedSpot.matchKey(): String? = SpotTitleNormalizer.lookupKey(title)

    /**
     * 표시용 제목. 비한국어는 한글을 걷어낸다 — 매칭 단서였을 뿐 표시할 값이 아니다.
     *
     * **null이면 그 언어의 제목이 아니다** — 한글만 남는 제목은 TourAPI가 번역을 주지 않고
     * 원문을 그대로 넣은 것이며, 호출자가 그 spot을 미배정으로 남긴다.
     */
    private fun UnassignedSpot.displayTitle(): String? =
        if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title)

    /**
     * 후보 중 이 spot의 카탈로그를 고른다. 좌표 일치가 없거나 둘 이상이면 붙이지 않고 새
     * 카탈로그를 만든다 — 잘못 붙이면 좋아요가 다른 장소로 딸려 간다.
     *
     * **KO와 비한국어의 기준이 다르다.**
     *  - KO끼리는 **항상 좌표까지 같아야** 접는다. 같은 이름이 전국에 여럿이므로(`만두집`,
     *    `롯데마트`) 제목만으로 접으면 무관한 가게가 한 장소가 된다.
     *  - 비한국어는 후보가 유일하면 좌표를 묻지 않는다. 괄호 한국어가 이미 그 장소를
     *    지목하고 있고, 언어별로 좌표가 미세하게 다른 경우가 실재한다.
     *
     * **근사 비교를 하지 않는다.** 좌표가 미세하게 다른 두 spot이 실제로 다른 가게인 경우와
     * 같은 가게의 좌표가 갱신된 경우를 거리로는 구분할 수 없다.
     */
    private fun List<CatalogCandidate>.pickFor(spot: UnassignedSpot): Long? {
        if (isEmpty()) return null
        if (!spot.language.isKorean) singleOrNull()?.let { return it.catalogId }
        return singleOrNull { it.mapX == spot.mapX && it.mapY == spot.mapY }?.catalogId
    }

    /**
     * 카탈로그의 표준값. **좌표 유효성은 조회 포트가 이미 걸렀다** — 여기 온 행은 전부 유효하다.
     */
    private fun UnassignedSpot.toStandardValues(): CatalogStandardValues =
        CatalogStandardValues(
            mapX = mapX,
            mapY = mapY,
            thumbnailImageUrl = thumbnailImageUrl,
            category = category,
            copyright = copyright,
        )

    /**
     * **번역이 비어 있는 (카탈로그, 언어) 쌍을 찾아 알린다**(R-4).
     *
     * 이 도메인은 번역하지 않고 **대상만 찾아 이벤트로 넘긴다** — 번역을 누가 어떻게 하는지는
     * tourism의 관심사가 아니다. 구독자가 그 목록을 받아 처리한다.
     *
     * 제목 해시 컬럼도, 변경 감지도 두지 않는다 — 재번역 경로가 존재하지 않으므로 필요 없다(R-7).
     * 기존 `content_id`의 값이 상류에서 바뀌어도 번역은 그대로 둔다. 의도된 동작이다.
     *
     * **KO 번역이 없는 카탈로그는 대상이 아니다**(포트가 제외한다) — 외국어에만 있는 장소이고,
     * 번역할 원문이 없다. 그 장소는 자기 언어 사용자에게만 보이며 그것이 올바른 동작이다.
     */
    @Transactional(readOnly = true)
    override fun translateMissing(languages: Set<DomainLocale>, limit: Int): TranslationResult {
        val candidates = queryPort.findCatalogsMissingTranslation(languages, limit)

        val targets = candidates.mapNotNull { catalog ->
            val wanted = catalog.missingLanguages intersect languages
            wanted.takeIf { it.isNotEmpty() }?.let {
                TranslationTarget(
                    catalogId = catalog.catalogId,
                    koreanTitle = catalog.koreanTitle,
                    koreanAddress = catalog.koreanAddress,
                    missingLanguages = it,
                )
            }
        }

        // **빈 목록은 발행하지 않는다** — 구독자가 빈 알림을 거르지 않아도 되게 한다.
        if (targets.isNotEmpty()) {
            eventPublisher.publish(TranslationTargetsFound(targets))
        }

        log.info(
            "catalog translation targets: found={} skipped={}",
            targets.size, candidates.size - targets.size,
        )
        return TranslationResult(translated = targets.size, skipped = candidates.size - targets.size)
    }

    /**
     * 원본이 사라진 카탈로그의 번역을 지워 조회에서 감춘다.
     *
     * 카탈로그와 좋아요는 남긴다 — 상류가 그 장소를 다시 주면 배정이 번역을 되살린다.
     */
    @Transactional
    override fun hideOrphanCatalogs(): Int {
        val hidden = storePort.purgeOrphanTranslations()
        if (hidden > 0) log.info("orphan catalogs hidden: translations={}", hidden)
        return hidden
    }

    private companion object {
        /**
         * 한 번의 배정(= 한 트랜잭션)에서 처리할 최대 행 수.
         *
         * **작게 잡는다.** 한 트랜잭션이 길수록 커넥션을 오래 점유하고, 마지막 한 건이
         * 실패하면 앞의 전부가 롤백되어 처음부터 다시 한다(실측: 500행 회차가 5분 30초를
         * 돌다 연결 유실).
         *
         * Spring Batch의 `CONTINUABLE`이 회차를 이어 주므로 작게 끊어도 전량 처리된다.
         */
        const val ASSIGNMENT_BATCH_SIZE = 200
    }
}
