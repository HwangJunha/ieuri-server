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
 *
 * 배정의 교정 경로다. `SpotCatalogService`와 나눠 둔 이유는 **다루는 포트가 다르기 때문이다** —
 * 이쪽은 공간 조회와 재배정을 쓰고 저쪽은 매칭 맵과 최초 배정을 쓴다. 한 서비스에 합치면
 * 생성자가 여섯 포트를 받고 어느 메서드가 무엇을 쓰는지 읽히지 않는다.
 *
 * 판정은 두 조건의 **AND**이며, 어느 한쪽만으로는 오탐이 난다(포트 KDoc 참조).
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

    /**
     * 카탈로그 하나를 가른다. 군집이 하나면 아무것도 하지 않는다 — 후보 판정은 원본 간 **최대**
     * 거리로 하는데, 세 점이 사슬처럼 이어져 있으면 최대 거리가 멀어도 한 군집일 수 있다.
     */
    private fun splitOne(catalogId: Long): CatalogSplitResult {
        val origins = spatialQueryPort.findOrigins(catalogId)
        val clusters = OriginProximityClustering.cluster(origins) { it.mapX to it.mapY }
        if (clusters.size < 2) return CatalogSplitResult(0, 0, 0)

        // **가장 큰 군집이 기존 id를 지킨다.** 좋아요·글 연결이 그 id에 매달려 있으므로 옮기는
        // 행이 적을수록 영향이 작다. 동수면 앞선 군집이 남는다(군집 순서는 입력 순서를 따른다).
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

        // **남는 군집에 없는 언어의 번역 행을 지운다.** 옛 카탈로그가 자기 것이 아닌 언어를
        // 주장하지 않게 한다 — 원본이 사라진 것이 아니라 다른 카탈로그로 갔으므로, 노출 갱신이
        // 되살릴 근거가 없어 감춤으로는 정리되지 않는다.
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
     * 군집 하나로 새 카탈로그를 만들고 그 언어들의 번역을 심는다.
     *
     * 표준값은 **대표 원본 하나**에서 온다 — KO가 있으면 KO, 없으면 언어 순서(KO→EN→JA→ZH)의
     * 첫 행이다. `SpotCatalog.from`이 최초 배정에서 따르는 규칙과 같다.
     *
     * **축제 기간도 심는다** — 기간은 저장한 행이 아니라 `spot_festival`에서 오므로 여기서
     * 빠뜨리면 갈라져 나온 축제는 기간을 영영 갖지 못한다.
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

        // 대표의 번역은 save가 이미 썼다. 나머지 언어만 덧붙인다 — 같은 언어가 둘이면
        // 대표와 같은 규칙(먼저 들어온 값 우선)으로 upsert가 뒤엣것을 무시한다.
        cluster.filter { it !== representative }.forEach { origin ->
            upsertPort.upsertTranslation(newCatalogId, origin.language, origin.displayTitle(), origin.address)
        }
        return newCatalogId
    }

    /**
     * 표시용 제목. 배정과 같은 규칙으로 비한국어의 병기 한국어를 걷어낸다.
     *
     * **여기서는 null로 떨어지지 않는다.** 배정이 표시 제목 없는 행을 애초에 붙이지 않으므로
     * 카탈로그에 매달린 행은 전부 제목이 있다. 그래도 정규화가 빈 값을 내면 원본 제목을 쓴다 —
     * 제목 없이 카탈로그를 만들 수 없고(`SpotCatalog.from`이 거부한다), 그 한 건 때문에 분할
     * 전체를 롤백하는 것보다 낫다.
     */
    private fun CatalogOrigin.displayTitle(): String = if (language.isKorean) title else SpotTitleNormalizer.displayTitle(title) ?: title

    private fun CatalogOrigin.toStandardValues(): CatalogStandardValues = CatalogStandardValues(
        mapX = mapX,
        mapY = mapY,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category,
        copyright = copyright,
    )

    private companion object {
        /**
         * 이 수를 **초과**하는 원본을 가진 카탈로그만 후보다. 언어가 4개뿐이라 그보다 많으면
         * 이미 무언가 접혔다는 뜻이다.
         */
        const val MIN_ORIGIN_COUNT = 4

        /** 원본 간 최대 거리가 이 값 **이상**이면 다른 장소로 본다. */
        const val MIN_DISTANCE_METERS = 10_000.0

        /** 표준값을 내줄 대표를 고르는 순서. `SpotCatalog.from`이 최초 배정에서 따르는 것과 같다. */
        val STANDARD_VALUE_PRIORITY =
            listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}
