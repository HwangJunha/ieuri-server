package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.tourism.application.exception.CursorExpiredException
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.port.out.client.CommunityActivityCountPort
import org.around.ieuri.tourism.application.port.out.client.ExternalTrendScorePort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.validation.coordinatesOf
import org.around.ieuri.tourism.application.validation.requireRadiusWithin
import org.around.ieuri.tourism.domain.strategy.DppItem
import org.around.ieuri.tourism.domain.strategy.SimilarityInput
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsCursor
import org.around.ieuri.tourism.domain.vo.ScoreFactors
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * `Location.recommendedSpots`(SPEC-0035 R4~R16). 반경 안 후보 N건에 점수를 매기고 DPP로 재배열한
 * 순열의 [position, position + first) 구간을 돌려준다. 요청마다 다시 계산한다 — 후보 순서(거리, id)와
 * 동점 규칙(id 오름차순)이 고정돼 같은 입력은 같은 순열이다(AC-4.1). 집계·유행 포트의 실패는 오류가
 * 아니라 요인 없음이다.
 */
@Service
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val activityCountPort: CommunityActivityCountPort,
    private val trendScorePort: ExternalTrendScorePort,
    private val spotQueryPolicy: SpotQueryPolicy,
    private val policy: RecommendationPolicy,
    private val clock: Clock,
) : RecommendedSpotsUseCase {

    private val log = LoggerFactory.getLogger(RecommendedSpotsQueryService::class.java)

    override fun recommendedSpots(
        query: LocationViewQuery,
        locale: DomainLocale,
        memberId: Long?,
    ): CursorPage<SpotView> {
        val center = coordinatesOf(query.latitude, query.longitude)
        requireRadiusWithin(query.radiusMeters, spotQueryPolicy.maxRadiusMeters)
        val first = query.first.coerceIn(1, spotQueryPolicy.maxPageSize)

        val now = clock.instant()
        val cursor = query.after?.let { decodeCursor(it, now) }
        val position = cursor?.position ?: 0
        // 세션 시작 시각은 이어 받아도 그대로다 — 수명이 세션 전체에 걸린다(R4).
        val issuedAt = cursor?.issuedAt ?: now

        val ordered = rerank(loadCandidates(query, center, locale, memberId), locale)
        // first + 1건을 잘라 초과분의 존재로 다음 페이지를 판정한다(keysetPageOf와 같은 규약).
        val cards = ordered.drop(position).take(first + 1)
        return cursorPageOf(
            edges = cards.take(first).mapIndexed { index, card ->
                CursorEdge(
                    cursor = RecommendedSpotsCursor(position + index + 1, issuedAt).encode(),
                    node = card.toView(),
                )
            },
            hasNextPage = cards.size > first,
            hasPreviousPage = query.after != null,
        )
    }

    /** 형식 → 수명 순으로 판정한다. */
    private fun decodeCursor(after: String, now: Instant): RecommendedSpotsCursor {
        val cursor = try {
            RecommendedSpotsCursor.decode(after)
        } catch (e: IllegalArgumentException) {
            throw InvalidCursorException(cause = e)
        }
        if (cursor.isExpired(now, policy.cursorMaxAge)) throw CursorExpiredException()
        return cursor
    }

    /** 거리 가까운 순 N건. 로그인 회원은 반응한 장소를 DB 단계에서 뺀다(R5). */
    private fun loadCandidates(
        query: LocationViewQuery,
        center: Coordinates,
        locale: DomainLocale,
        memberId: Long?,
    ): List<SpotSearchResult> {
        val excluded = memberId?.let { spotQueryPort.findReactedSpotIds(it, locale) }.orEmpty()
        return spotSpatialQueryPort.searchNearby(
            NearbySpotSearch(
                language = locale,
                center = center,
                radiusMeters = query.radiusMeters,
                categories = query.categories,
                excludedSpotIds = excluded,
                limit = policy.candidateCap,
                offset = 0,
            ),
        )
    }

    /**
     * 점수가 있는 후보는 DPP 그리디로, 없는 후보는 맨 뒤에 id 순으로(AC-6.2).
     * 유사도는 카테고리 대체 거리다(R14) — 벡터는 S2에서 붙는다.
     */
    private fun rerank(cards: List<SpotSearchResult>, locale: DomainLocale): List<SpotSearchResult> {
        if (cards.isEmpty()) return cards
        val qualities = score(cards, locale)
        val byId = cards.associateBy { it.spotId }
        val inputs = cards.associate { it.spotId to SimilarityInput(category = it.category) }
        val scored = cards.mapNotNull { card -> qualities[card.spotId]?.let { DppItem(card.spotId, it) } }
        val unscored = cards.filter { qualities[it.spotId] == null }.sortedBy { it.spotId }

        val ordered = policy.reranker.rerank(scored) { a, b ->
            policy.similarity.distance(inputs.getValue(a.id), inputs.getValue(b.id))
        }
        return ordered.map { byId.getValue(it) } + unscored
    }

    /** 후보 전체의 요인을 한 번에 모아 점수를 낸다. 집계 포트가 모르는 장소는 그 요인이 없다. */
    private fun score(cards: List<SpotSearchResult>, locale: DomainLocale): Map<Long, Double?> {
        val ids = cards.map { it.spotId }
        val reactions = factorSource("반응 집계") { spotQueryPort.countReactionsIn(ids, locale) }
        val activities = factorSource("활동 집계") { activityCountPort.countByCatalogIds(ids) }
        val trends = factorSource("유행 점수") { trendScorePort.findByCatalogIds(ids) }

        return cards.associate { card ->
            val factors = ScoreFactors(
                distance = policy.score.distanceFactor(card.distanceMeters),
                popularity = reactions[card.spotId]?.let { policy.score.popularityFactor(it.likes, it.dislikes) },
                activity = activities[card.spotId]?.let { policy.score.activityFactor(it.posts, it.questions, it.answers) },
                trend = trends[card.spotId],
            )
            card.spotId to policy.score.score(factors, card.title)
        }
    }

    /** 요인 출처의 실패는 오류가 아니라 요인 없음이다(§ 검증과 오류). */
    private fun <T> factorSource(name: String, read: () -> Map<Long, T>): Map<Long, T> =
        try {
            read()
        } catch (e: RuntimeException) {
            log.warn("추천 목록 {} 조회 실패, 요인 없음으로 진행: {}", name, e.javaClass.simpleName)
            emptyMap()
        }
}
