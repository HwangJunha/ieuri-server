package org.around.ieuri.tourism.application.service

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.event.SpotDetailAbsent
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.event.SpotGalleryFetched
import org.around.ieuri.tourism.application.event.SpotInfoItemsFetched
import org.around.ieuri.tourism.application.event.SpotIntroFetched
import org.around.ieuri.tourism.application.exception.EmptyQueryException
import org.around.ieuri.tourism.application.exception.InvalidCoordinatesException
import org.around.ieuri.tourism.application.exception.QueryTooLongException
import org.around.ieuri.tourism.application.exception.RadiusTooLargeException
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.exception.SpotNotFoundInLocaleQueryException
import org.around.ieuri.tourism.application.exception.SpotQueryException
import org.around.ieuri.tourism.application.exception.UnknownCategoryException
import org.around.ieuri.tourism.application.policy.SpotQueryPolicy
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.port.out.cache.SpotCachePort
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.application.port.out.persistence.StoredDetail
import org.around.ieuri.tourism.application.port.out.query.AppCategoryQueryPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotSearchPort
import org.around.ieuri.tourism.application.port.out.query.LikedSpotsSearch
import org.around.ieuri.tourism.application.port.out.query.NearbySpotSearch
import org.around.ieuri.tourism.application.port.out.query.SpotIdentity
import org.around.ieuri.tourism.application.port.out.query.SpotQueryPort
import org.around.ieuri.tourism.application.port.out.query.SpotSearchResult
import org.around.ieuri.tourism.application.port.out.query.SpotSpatialQueryPort
import org.around.ieuri.tourism.application.query.AppCategoryCode
import org.around.ieuri.tourism.application.query.AppCategoryView
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotInfoItemView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotSummaryView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 관광지 조회 유스케이스 구현(CQRS 읽기). 목록과 상세의 데이터 출처가 다르다:
 *  - 목록 : 적재된 spot 테이블만 본다. 반경 필터·거리 계산·정렬은 DB가 인덱스로 수행한다.
 *  - 상세 : Valkey → DB → TourAPI 3단 폴백. 앞 단계에서 값을 찾으면 뒤 단계를 호출하지 않으며,
 *           필드별 지연 조회 구조를 유지한다.
 *
 * 페이지네이션은 오프셋(page/size)이다.
 */
@Service
@Transactional(readOnly = true)
class SpotQueryService(
    private val tourInfoPort: TourInfoPort,
    private val spotQueryPort: SpotQueryPort,
    // 반경 조회만 PostGIS 생 SQL이라 포트가 갈린다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val spotSpatialQueryPort: SpotSpatialQueryPort,
    // 좋아요 목록 내 검색만 검색 노드(BM25)를 본다 — 어느 읽기가 그쪽인지 여기서 보인다.
    private val likedSpotSearchPort: LikedSpotSearchPort,
    private val spotCachePort: SpotCachePort,
    private val spotDetailStorePort: SpotDetailStorePort,
    private val eventPublisher: TourismEventPublisherPort,
    private val appCategoryQueryPort: AppCategoryQueryPort,
    private val policy: SpotQueryPolicy,
) : SpotQueryUseCase {

    /**
     * 주변 관광지 한 페이지. **TourAPI를 호출하지 않는다** — 배치가 적재한 spot 테이블만 본다.
     * 반경 필터·거리 계산·정렬은 DB가 인덱스로 수행하며, 정렬은 거리 오름차순 + id 타이브레이크다.
     */
    override fun spots(query: SpotsQuery, locale: DomainLocale, memberId: Long?): SpotPageView {
        val center = validatedCenter(query.latitude, query.longitude)
        validateRadius(query.radiusMeters)

        val page = query.page.coerceAtLeast(1)
        val size = query.size.coerceIn(1, policy.maxPageSize)

        // 로그인 회원이 이미 반응(좋아요/패스)한 관광지 제외 — 중복 노출 방지. 비로그인은 제외 없음.
        // **DB 쿼리 단계에서 제외한다** — 조회 후 걸러내면 페이지가 요청한 size보다 작아진다.
        // **제외도 활성 언어 단위다**(SPEC-0029 R-13) — 다른 언어에서 누른 반응까지 제외하면
        // 이 언어에서는 아직 보지 못한 장소를 잃는다.
        val excluded = memberId?.let { spotQueryPort.findReactedSpotIds(it, locale) }.orEmpty()

        val search = NearbySpotSearch(
            language = locale,
            center = center,
            radiusMeters = query.radiusMeters,
            categories = query.categories,
            excludedSpotIds = excluded,
            limit = size,
            offset = (page - 1) * size,
        )

        val results = spotSpatialQueryPort.searchNearby(search)
        val totalCount = spotSpatialQueryPort.countNearby(search)

        return SpotPageView(
            items = results.map { it.toView() },
            page = page,
            // 같은 조건의 전체 건수 기준이라 totalCount와 일관된다 — 둘이 갈리면
            // hasNext=false인데 totalCount는 더 있다고 말하는 상태가 생긴다(런타임 계약 위험 3번).
            hasNext = page.toLong() * size < totalCount.toLong(),
            totalCount = totalCount,
        )
    }

    override fun availableCategories(appKey: AppKey, locale: DomainLocale): List<AppCategoryView> = appCategoryQueryPort.findCategories(appKey, locale)

    override fun availableCategoryCodes(appKey: AppKey, languageCode: String): List<AppCategoryCode> = availableCategories(appKey, DomainLocale.fromLanguageTag(languageCode))
        .map {
            AppCategoryCode(
                category = it.category.code,
                label = it.label,
                iconUrl = it.iconUrl,
            )
        }

    /** 폴백까지 채워 돌려준다 — 호출자가 코드마다 빈 값을 분기하지 않게 한다. */
    override fun categoryDisplays(codes: Set<String>, locale: DomainLocale): Map<String, CategoryDisplay> {
        val found = appCategoryQueryPort.findDisplays(codes, locale)
        return codes.associateWith { found[it] ?: CategoryDisplay.fallback(SpotCategory(it)) }
    }

    /**
     * 상세 본문(§4). 캐시를 먼저 보고 미스일 때만 upstream(detailCommon2 1콜) 후 저장한다(히트면 upstream
     * 무소모 — AC-19). 오류는 예외로 전파되므로 캐시에 남지 않는다.
     * 갤러리·운영정보·이용안내·좋아요 수는 각 필드가 요청될 때 아래 메서드로 따로 조회한다.
     *
     * **부재와 실패를 절대 섞지 않는다**(SPEC-0008 R-1·AC-4). 업스트림에 본문이 *없는* 것은 다시
     * 물어도 결과가 같은 사실이므로 적재해 두고 성공으로 응답한다. 업스트림 호출이 *깨진* 것은
     * 다음엔 될 수 있으므로 적재하지 않고 예외를 그대로 올린다. 섞으면 일시적 장애가 부재로 적재되어
     * 다음 재확보 주기까지 데이터가 복구되지 않는다.
     *
     * 세 가지 상태를 구분한다(R-3):
     *   - 미조회      : 캐시·DB 모두 미스        → upstream 호출 (AC-10)
     *   - 조회함·부재 : bodyAbsent = true       → upstream 호출 **안 함**(SPEC-0037 FR-011)
     *   - 조회함·존재 : bodyAbsent = false      → upstream 호출 안 함
     *
     * **부재 행을 조회 시점에 재확인하지 않는다.** SPEC-0008 R-4의 7일 재조회를 SPEC-0037이
     * 걷어냈고, 재확보는 30일 주기 배치가 맡는다 — 그래야 재확보 주체가 하나로 모여 호출량이
     * 예측 가능해진다.
     */
    override fun spotDetail(catalogId: Long, locale: DomainLocale): SpotDetailView {
        val identity = resolve(catalogId, locale)
        // **캐시·`spot_detail` 계열의 키는 원본 `spot.id`다.** 카탈로그 id를 쓰면 FK가
        // `spot(id)`를 가리키는 자식 테이블에 적재가 실패하고(리스너가 삼킨다), 값이 우연히
        // 겹치면 다른 장소의 본문이 적재된다.
        val spotId = identity.spotId

        // 캐시 히트: 부재도 캐시된 값이다 — 미스로 오판하면 부재 spot이 TTL 내내 DB·upstream을 탄다.
        spotCachePort.getDetail(spotId)?.let { return it.detail.toView(catalogId, identity) }

        // DB 히트: Valkey를 충전하고 upstream은 건드리지 않는다(AC-35).
        // **부재 행도 그대로 응답한다**(SPEC-0037 FR-011) — 조회를 계기로 원본에 다시 묻지 않는다.
        // 재확보는 30일 주기 배치(`spotDetailPrefetchJob`)가 맡는다.
        spotDetailStorePort.findDetail(spotId)?.let { stored ->
            if (stored.bodyAbsent) {
                spotCachePort.putDetailAbsence(spotId, stored.detail)
            } else {
                spotCachePort.putDetail(spotId, stored.detail)
            }
            return stored.detail.toView(catalogId, identity)
        }

        // 양쪽 미스: upstream 조회 후 캐시 충전 + 적재 이벤트 발행(AC-36·AC-39).
        // 여기서 던져지는 예외는 **실패**이며 부재로 적재되지 않는다 — 이벤트 발행 전에 전파된다(AC-4·AC-16).
        val fetched = tourInfoPort.findDetail(identity.toQuery(locale))

        if (fetched == null) {
            // 부재 확정. `spot` 행의 값으로 응답을 구성하고(R-2) 부재 사실을 적재한다(R-3).
            // 재조회 결과가 여전히 부재여도 적재 시각이 갱신되어 다음 신선도 창이 새로 시작된다(AC-15).
            val fallback = bodyAbsentDetail(catalogId, locale)
            spotCachePort.putDetailAbsence(spotId, fallback)
            eventPublisher.publish(SpotDetailAbsent(spotId, fallback))
            return fallback.toView(catalogId, identity)
        }

        // 재조회 결과 본문이 생겼다면 부재 행이 본문 있는 상태로 전환된다(AC-14) — saveDetail의 upsert가
        // body_absent를 false로 되돌린다.
        spotCachePort.putDetail(spotId, fetched)
        eventPublisher.publish(SpotDetailFetched(spotId, fetched))
        return fetched.toView(catalogId, identity)
    }

    /**
     * 본문 부재 시 응답·적재의 재료(R-2). `spot` 행이 title·category·thumbnailImageUrl·copyright를
     * 전부 non-null로 갖고 있어 본문이 없어도 유효한 상세를 구성할 수 있다(AC-5·AC-6).
     * overview·homepage·tel은 null로 남는다(AC-7). address는 `spot`이 갖고 있으면 쓰고 없으면 null이다(AC-8).
     *
     * [resolve]가 이미 통과한 뒤이므로 행은 존재한다 — 그 사이 사라졌다면 부재가 아니라 미해석이다.
     *
     * **언어 폴백이 아니다**(SPEC-0029). "본문이 없을 때 카탈로그 값으로 상세를 구성한다"는 뜻이며,
     * 값은 활성 언어의 번역에서만 온다.
     */
    private fun bodyAbsentDetail(catalogId: Long, language: DomainLocale): SpotDetail {
        // **카탈로그 id로 묻는다** — findSpotBasics는 `spot_catalog`를 조회한다.
        val basics = spotQueryPort.findSpotBasics(catalogId, language) ?: throw spotAbsenceFor(catalogId)
        return SpotDetail(
            title = basics.title,
            category = basics.category,
            thumbnailImageUrl = basics.thumbnailImageUrl,
            overview = null,
            address = basics.address,
            homepage = null,
            tel = null,
            copyright = basics.copyright,
        )
    }

    override fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImageView> {
        val identity = resolve(catalogId, locale)
        val spotId = identity.spotId
        spotCachePort.getGallery(spotId)?.let { return it.map { image -> image.toView() } }

        spotDetailStorePort.findGallery(spotId)?.let { stored ->
            spotCachePort.putGallery(spotId, stored)
            return stored.map { it.toView() }
        }

        val fetched = tourInfoPort.findGallery(identity.toQuery(locale))
        spotCachePort.putGallery(spotId, fetched)
        eventPublisher.publish(SpotGalleryFetched(spotId, fetched))
        return fetched.map { it.toView() }
    }

    override fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntroView? {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        // 캐시·저장 키는 원본 `spot.id`다 — 카탈로그 id를 쓰면 자식 테이블의 FK와 어긋난다.
        val spotId = identity.spotId
        // 히트 판정은 래퍼(non-null)로 한다 — 값 자체는 정상적으로 null일 수 있어, value로 판정하면
        // "값 없음"이 매번 캐시 미스로 취급돼 upstream을 계속 두드리게 된다.
        val cached = spotCachePort.getIntro(spotId, category)
        if (cached != null) return cached.value?.toView()

        // DB도 같은 이유로 래퍼를 쓴다 — "적재됐으나 값 없음"을 미스로 오판하면 upstream을 계속 친다.
        val stored = spotDetailStorePort.findIntro(spotId, category)
        if (stored != null) {
            spotCachePort.putIntro(spotId, category, stored.value)
            return stored.value?.toView()
        }

        val intro = tourInfoPort.findIntro(identity.toQuery(locale), category)
        spotCachePort.putIntro(spotId, category, intro)
        eventPublisher.publish(SpotIntroFetched(spotId, category, intro))
        return intro?.toView()
    }

    override fun spotInfoItems(
        catalogId: Long,
        category: SpotCategory,
        locale: DomainLocale,
    ): List<SpotInfoItemView> {
        requireKnownCategory(category)
        val identity = resolve(catalogId, locale)
        // 캐시·저장 키는 원본 `spot.id`다 — 카탈로그 id를 쓰면 자식 테이블의 FK와 어긋난다.
        val spotId = identity.spotId
        spotCachePort.getInfoItems(spotId, category)
            ?.let { return it.map { item -> SpotInfoItemView(item.name, item.text) } }

        spotDetailStorePort.findInfoItems(spotId, category)?.let { stored ->
            spotCachePort.putInfoItems(spotId, category, stored)
            return stored.map { SpotInfoItemView(it.name, it.text) }
        }

        val fetched = tourInfoPort.findInfoItems(identity.toQuery(locale), category)
        spotCachePort.putInfoItems(spotId, category, fetched)
        eventPublisher.publish(SpotInfoItemsFetched(spotId, category, fetched))
        return fetched.map { SpotInfoItemView(it.name, it.text) }
    }

    /**
     * 좋아요 수는 캐시하지 않고 매번 집계한다(AC-14).
     * **3단 폴백 경로에 넣지 않는다**(AC-38) — spot_reaction 기반이라 TourInfoPort와 완전히 다른
     * 포트이며, write-behind는 upstream 파생 데이터만 다룬다.
     */
    override fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long = spotQueryPort.countLikes(catalogId, locale)

    /**
     * 최신 반응 코드. 좋아요 수와 같은 이유로 캐시 비대상이다 — 판정 시점의 상태여야 하고
     * (SPEC-0026 R5), 반응은 언제든 덮어써진다.
     */
    override fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): String? = spotQueryPort.findReaction(catalogId, memberId, locale)?.name

    /**
     * 카탈로그 여러 행을 읽는다 — 캐시도 upstream도 타지 않는다.
     *
     * **[languageCode]에서 해소되지 않는 id는 결과에 키가 없다**(SPEC-0029 R-24·AC-24.3). 호출자는
     * 그것을 "이 언어에 없는 장소"로 해석한다 — 존재 검사도 이 규칙을 따르므로, 활성 언어에
     * 없는 spot은 첨부·조회 어느 쪽에서도 성립하지 않는다(R-25).
     */
    override fun spotSummaries(catalogIds: List<Long>, languageCode: String): Map<Long, SpotSummaryView> = spotQueryPort.findSpotBasicsIn(catalogIds, DomainLocale.fromLanguageTag(languageCode))
        .mapValues { (catalogId, basics) ->
            SpotSummaryView(
                id = catalogId,
                title = basics.title,
                thumbnailImageUrl = basics.thumbnailImageUrl,
                categoryCode = basics.category.code,
                longitude = basics.longitude,
                latitude = basics.latitude,
            )
        }

    /**
     * 카탈로그 한 행만 읽는다. **[languageCode]에서 해소되지 않으면 null이다**(AC-24.2) — 다른 언어
     * 이름으로 대신하지 않는다. 호출자가 그것을 "이 언어에 없는 장소"로 해석한다.
     */
    override fun spotSummary(catalogId: Long, languageCode: String): SpotSummaryView? = spotQueryPort.findSpotBasics(catalogId, DomainLocale.fromLanguageTag(languageCode))?.let {
        SpotSummaryView(
            id = catalogId,
            title = it.title,
            thumbnailImageUrl = it.thumbnailImageUrl,
            categoryCode = it.category.code,
            longitude = it.longitude,
            latitude = it.latitude,
        )
    }

    /**
     * 좋아요 목록(SPEC-0008 R-15~R-17). LIKE만·본인 것만·요청 언어만 — 세 조건 모두 조회 포트가
     * 쿼리 단계에서 건다(AC-45·AC-48·AC-49). 여기서는 페이지 크기 상한만 적용한다(AC-52).
     *
     * **애그리거트를 우회한다** — 읽기 전용이고 `spot` 행의 값만 필요하므로 QueryDSL 프로젝션이
     * read DTO를 직접 만든다. 상세(`spot_detail`)를 조인하지 않아 상세가 부재한 spot도 목록에 나온다.
     *
     */
    override fun likedSpots(
        query: LikedSpotsQuery,
        locale: DomainLocale,
        memberId: Long,
    ): CursorPage<LikedSpotView> = // 카테고리 가용성 판정은 spots와 같다 — 같은 축을 쓰므로 달라질 이유가 없다.
        spotQueryPort.findLikedSpots(searchOf(query, locale, memberId))

    /**
     * 좋아요 목록 내 검색(SPEC-0033). **[likedSpots]와 조회처가 다르다** — 오타·형태소 매칭이
     * pg_search에만 있어 검색 노드를 본다. 정렬·커서 규약은 같고 신선도만 다르며, 그 차이가
     * 유스케이스를 나눈 이유다.
     *
     * 검증은 포트 호출보다 먼저다(R6) — 이 위치라야 어댑터 없이 단위 테스트로 고정된다.
     * 상한은 `searchSpots`와 같은 [SpotQueryPolicy.maxSearchQueryLength]를 쓴다.
     */
    override fun searchLikedSpots(
        query: LikedSpotsQuery,
        text: String,
        locale: DomainLocale,
        memberId: Long,
    ): CursorPage<LikedSpotView> = likedSpotSearchPort.searchLikedSpots(searchOf(query, locale, memberId), validatedSearchText(text))

    /** 두 조회가 같은 조건을 쓰도록 한 곳에서 만든다 — 갈리면 페이지 상한·언어 한정이 어긋난다. */
    private fun searchOf(query: LikedSpotsQuery, locale: DomainLocale, memberId: Long) = LikedSpotsSearch(
        memberId = memberId,
        language = locale,
        categories = query.categories,
        first = query.first.coerceIn(1, policy.maxLikedPageSize),
        after = query.after,
    )

    /**
     * 제출된 검색어를 트림해 돌려준다. 트림 후 비었으면 EMPTY_QUERY, 상한 초과면 QUERY_TOO_LONG.
     *
     * **길이는 트림 후를 센다** — 앞뒤 공백으로 상한을 넘기는 것은 사용자의 의도가 아니다.
     * 예외 메시지에 원문을 담지 않는다(POL-013).
     */
    private fun validatedSearchText(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> throw EmptyQueryException()
            trimmed.length > policy.maxSearchQueryLength -> throw QueryTooLongException(policy.maxSearchQueryLength)
            else -> trimmed
        }
    }

    /**
     * 카탈로그 id를 **활성 언어의 원본 행으로** 해석한다(SPEC-0019, SPEC-0029 R-9·R-10).
     *
     * **캐시·DB를 보기 전에 호출해야 한다.** 해석을 upstream 조회 직전에 두면 캐시 히트·DB 히트에서
     * 그대로 통과해, 정작 흔한 경로에서 교차 언어 콘텐츠가 나간다. `spot_detail`과 캐시 키 모두
     * 언어를 들지 않으므로(V20) 그 단계에서는 언어를 스스로 판별할 수 없다.
     *
     * **폴백하지 않는다.** 활성 언어에 원본이 없으면 다른 언어 값으로 대신하지 않고
     * `SPOT_NOT_FOUND_IN_LOCALE`로 실패한다 — 없는 것은 없는 것이다(POL-043).
     * 카탈로그 자체가 없는 경우와는 다른 코드다(AC-12.3).
     */
    private fun resolve(catalogId: Long, locale: DomainLocale): SpotIdentity = spotQueryPort.findIdentity(catalogId, locale) ?: throw spotAbsenceFor(catalogId)

    /**
     * 해소 실패의 원인을 갈라 예외를 만든다. 카탈로그가 있으면 "이 언어에 없음", 없으면 "그런 장소 없음".
     *
     * **카탈로그 존재 확인은 실패 경로에서만 한다** — 성공 경로에 조회를 하나 더 붙이지 않는다.
     */
    private fun spotAbsenceFor(catalogId: Long): SpotQueryException = if (spotQueryPort.existsCatalog(catalogId)) {
        SpotNotFoundInLocaleQueryException()
    } else {
        SpotDetailNotFoundException()
    }

    /**
     * upstream 조회 좌표. TourAPI는 우리 PK를 모르므로 해석된 contentId로 묻는다.
     * contentId가 언어별이라 [locale]은 이 contentId를 해석한 언어여야 한다.
     */
    private fun SpotIdentity.toQuery(locale: DomainLocale) = SpotDetailQuery(contentId = contentId, locale = locale)

    private fun validatedCenter(latitude: Double, longitude: Double): Coordinates = try {
        Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
        throw InvalidCoordinatesException(e.message ?: "유효하지 않은 좌표입니다.")
    }

    private fun validateRadius(radiusMeters: Int) {
        if (radiusMeters <= 0) throw InvalidCoordinatesException("반경은 양수여야 합니다.")
        if (radiusMeters > policy.maxRadiusMeters) {
            throw RadiusTooLargeException("반경은 최대 ${policy.maxRadiusMeters}m까지 허용됩니다.")
        }
    }

    /**
     * 정의된 카테고리인가. **필터가 아니라 상세 하위 필드에만 건다** — 여기서는 코드가 캐시 키와
     * 적재 행의 일부가 되므로, 막지 않으면 임의의 문자열마다 키와 빈 행이 하나씩 쌓인다.
     *
     * 유효 집합은 `category` 테이블이 정하므로 새 카테고리는 넣는 즉시 통과한다.
     */
    private fun requireKnownCategory(category: SpotCategory) {
        if (!appCategoryQueryPort.exists(category.code)) {
            throw UnknownCategoryException("알 수 없는 카테고리입니다: ${category.code}")
        }
    }

    /**
     * 어댑터가 만든 본문에 카탈로그 id와 좌표를 얹는다 — 캐시·DB·upstream 어느 경로도 우리 PK와
     * 좌표를 모른다.
     *
     * **좌표는 본문 출처와 무관하게 [identity]에서만 온다**(SPEC-0027).
     */
    private fun SpotDetail.toView(catalogId: Long, identity: SpotIdentity): SpotDetailView = SpotDetailView(
        id = catalogId,
        title = title,
        category = category,
        thumbnailImageUrl = thumbnailImageUrl,
        overview = overview,
        address = address,
        homepage = homepage,
        tel = tel,
        copyright = copyright,
        attributionText = attributionText,
        mapX = identity.mapX,
        mapY = identity.mapY,
    )

    private fun SpotIntro.toView(): SpotIntroView = SpotIntroView(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

    private fun SpotImage.toView(): SpotImageView = SpotImageView(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = copyright,
        attributionText = attributionText,
    )

    /**
     * 읽기 DTO → 응답 뷰. [attributionText]는 copyright에서 파생되므로 여기서 도출한다 —
     * 적재하지 않는 이유이기도 하다(컬럼으로 두면 둘이 어긋날 수 있다).
     * 거리는 DB가 계산한 값을 그대로 쓴다.
     */
    private fun SpotSearchResult.toView(): SpotView = SpotView(
        id = spotId,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = category,
        copyright = copyright,
        attributionText = copyright.attributionText,
        mapX = mapX,
        mapY = mapY,
    )
}
