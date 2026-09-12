package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.tourism.application.exception.CursorExpiredException
import org.around.ieuri.tourism.application.exception.InvalidCursorException
import org.around.ieuri.tourism.application.policy.RecommendationPolicy
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.RecommendedSpotsUseCase
import org.around.ieuri.tourism.application.port.out.cache.RecommendedSpotsBatchCachePort
import org.around.ieuri.tourism.application.port.out.client.CommunityActivityCountPort
import org.around.ieuri.tourism.application.port.out.client.ExternalTrendScorePort
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.LocationViewQuery
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.domain.strategy.DppItem
import org.around.ieuri.tourism.domain.strategy.SimilarityInput
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsConditions
import org.around.ieuri.tourism.domain.vo.RecommendedSpotsCursor
import org.around.ieuri.tourism.domain.vo.ScoreFactors
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * `Location.recommendedSpots`(SPEC-0035 R4~R17). 반경 안 후보 N건에 점수를 매기고 DPP로 재배열한
 * 순열을 묶음 B건씩 확정한다. 응답은 그 순열의 [position, position + first) 구간이다.
 *
 * 묶음 b는 "앞 묶음들을 뺀 후보"의 순열 앞 B건이라 앞 묶음이 있어야 만들 수 있다 — 캐시에 있으면
 * 그것을, 없으면 같은 입력으로 다시 계산한다. 후보 순서·동점 규칙이 고정돼 있어 재계산은 같은
 * 순열을 낸다(AC-4.1). 캐시·집계·유행 포트의 실패는 오류가 아니라 요인 없음·재계산이다.
 */
@Service
class RecommendedSpotsQueryService(
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    private val activityCountPort: CommunityActivityCountPort,
    private val trendScorePort: ExternalTrendScorePort,
    private val batchCachePort: RecommendedSpotsBatchCachePort,
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

        val conditions = RecommendedSpotsConditions(
            center = center,
            radiusMeters = query.radiusMeters,
            categories = query.categories,
            language = locale,
            memberId = memberId,
        )
        val now = clock.instant()
        val cursor = query.after?.let { decodeCursor(it, conditions, now) }
        val position = cursor?.position ?: 0
        // 세션 시작 시각은 이어 받아도 그대로다 — 수명이 세션 전체에 걸린다(R4).
        val issuedAt = cursor?.issuedAt ?: now

        val session = RecommendationSession(conditions)
        // first + 1건을 읽어 초과분의 존재로 다음 페이지를 판정한다(keysetPageOf와 같은 규약).
        val cards = session.slice(from = position, count = first + 1)
        return cursorPageOf(
            edges = cards.take(first).mapIndexed { index, card ->
                CursorEdge(
                    cursor = RecommendedSpotsCursor(conditions.hash, position + index + 1, issuedAt).encode(),
                    node = card.toView(),
                )
            },
            hasNextPage = cards.size > first,
            hasPreviousPage = query.after != null,
        )
    }

    /** 형식 → 조건 해시 → 수명 순으로 판정한다. 앞 단계가 실패하면 뒤를 보지 않는다. */
    private fun decodeCursor(after: String, conditions: RecommendedSpotsConditions, now: Instant): RecommendedSpotsCursor {
        val cursor = try {
            RecommendedSpotsCursor.decode(after)
        } catch (e: IllegalArgumentException) {
            throw InvalidCursorException(cause = e)
        }
        if (cursor.conditionsHash != conditions.hash) {
            throw InvalidCursorException("커서의 조건이 요청과 다릅니다.")
        }
        if (cursor.isExpired(now, policy.cursorMaxAge)) throw CursorExpiredException()
        return cursor
    }

    /**
     * 한 요청 동안의 순열 계산 상태. 후보·집계는 첫 캐시 미스에서 한 번만 읽고, 만든 묶음은
     * 요청 안에서 다시 쓴다. 요청마다 새로 만들어지므로 공유 가변 상태가 아니다.
     */
    private inner class RecommendationSession(private val conditions: RecommendedSpotsConditions) {
        private val batches = HashMap<Int, List<SpotSearchResult>>()
        private val candidates: List<SpotSearchResult> by lazy { loadCandidates() }
        private val qualities: Map<Long, Double?> by lazy { scoreCandidates(candidates) }

        /** 순열의 [from, from + count) 구간. 후보가 바닥나면 있는 만큼이다. */
        fun slice(from: Int, count: Int): List<SpotSearchResult> {
            val out = ArrayList<SpotSearchResult>(count)
            var batchNo = from / policy.batchSize
            var offset = from % policy.batchSize
            while (out.size < count) {
                val batch = batch(batchNo) ?: break
                out += batch.drop(offset).take(count - out.size)
                // B건을 못 채운 묶음이 마지막 묶음이다.
                if (batch.size < policy.batchSize) break
                batchNo++
                offset = 0
            }
            return out
        }

        /** [batchNo]번째 묶음. 후보가 바닥나 만들 수 없으면 null. */
        private fun batch(batchNo: Int): List<SpotSearchResult>? {
            batches[batchNo]?.let { return it }
            cached(batchNo)?.let {
                batches[batchNo] = it
                return it
            }

            val priorIds = HashSet<Long>()
            for (prior in 0 until batchNo) {
                val priorBatch = batch(prior) ?: return null
                priorBatch.forEach { priorIds += it.spotId }
                if (priorBatch.size < policy.batchSize) return null
            }
            val remaining = candidates.filter { it.spotId !in priorIds }
            if (remaining.isEmpty()) return null

            val built = rerank(remaining).take(policy.batchSize)
            batches[batchNo] = built
            remember(batchNo, built)
            return built
        }

        /** 캐시 조회 실패는 미스다(AC-17.3) — 캐시는 있으면 빠르고 없으면 정확한 것이다. */
        private fun cached(batchNo: Int): List<SpotSearchResult>? =
            tolerating("묶음 캐시 조회", null) { batchCachePort.get(conditions.hash, batchNo) }

        /** 캐시 저장 실패는 no-op이다(AC-17.3). */
        private fun remember(batchNo: Int, cards: List<SpotSearchResult>) =
            tolerating("묶음 캐시 저장", Unit) { batchCachePort.put(conditions.hash, batchNo, cards) }

        /**
         * 점수가 있는 후보는 DPP 그리디로, 없는 후보는 맨 뒤에 id 순으로(AC-6.2).
         * 유사도는 카테고리 대체 거리다(R14) — 벡터는 S2에서 붙는다.
         */
        private fun rerank(cards: List<SpotSearchResult>): List<SpotSearchResult> {
            val byId = cards.associateBy { it.spotId }
            val inputs = cards.associate { it.spotId to SimilarityInput(category = it.category) }
            val scored = cards.mapNotNull { card -> qualities[card.spotId]?.let { DppItem(card.spotId, it) } }
            val unscored = cards.filter { qualities[it.spotId] == null }.sortedBy { it.spotId }

            val ordered = policy.reranker.rerank(scored) { a, b ->
                policy.similarity.distance(inputs.getValue(a.id), inputs.getValue(b.id))
            }
            return ordered.map { byId.getValue(it) } + unscored
        }

        /** 거리 가까운 순 N건. 로그인 회원은 반응한 장소를 DB 단계에서 뺀다(R5). */
        private fun loadCandidates(): List<SpotSearchResult> {
            val excluded = conditions.memberId?.let { spotQueryPort.findReactedSpotIds(it, conditions.language) }.orEmpty()
            return spotSpatialQueryPort.searchNearby(
                NearbySpotSearch(
                    language = conditions.language,
                    center = conditions.center,
                    radiusMeters = conditions.radiusMeters,
                    categories = conditions.categories,
                    excludedSpotIds = excluded,
                    limit = policy.candidateCap,
                    offset = 0,
                ),
            )
        }

        /** 후보 전체의 요인을 한 번에 모아 점수를 낸다. 집계 포트가 모르는 장소는 그 요인이 없다. */
        private fun scoreCandidates(cards: List<SpotSearchResult>): Map<Long, Double?> {
            if (cards.isEmpty()) return emptyMap()
            val ids = cards.map { it.spotId }
            val reactions = factorSource("반응 집계") { spotQueryPort.countReactionsIn(ids, conditions.language) }
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
            tolerating(name, emptyMap(), read)

        /** 캐시·집계·유행 포트의 실패는 오류가 아니다 — [fallback]으로 진행하고 기록만 남긴다. */
        private fun <T> tolerating(name: String, fallback: T, action: () -> T): T =
            try {
                action()
            } catch (e: RuntimeException) {
                log.warn("추천 목록 {} 실패, 없는 것으로 진행: {}", name, e.javaClass.simpleName)
                fallback
            }
    }
}
