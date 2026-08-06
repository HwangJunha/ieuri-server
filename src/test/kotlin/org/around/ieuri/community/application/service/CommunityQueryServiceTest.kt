package org.around.ieuri.community.application.service

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커뮤니티 조회 서비스.
 *
 * 두 가지를 고정한다:
 *  1. **차단 컨텍스트를 세 경로가 모두 거친다**(피드·단건·댓글). 한 곳만 빠뜨려도 차단이
 *     무력화되며, 특히 단건은 딥링크 진입점이라 거기가 뚫리면 피드 필터가 소용없다.
 *  2. **피드 범위는 인자 조합이 정한다** — 대상 하나 / 좌표 반경 / 전체.
 */
class CommunityQueryServiceTest {

    private val memberId = 7L

    private val queryPort = mockk<CommunityQueryPort>()
    private val spotLookup = mockk<SpotLookupPort>()

    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        maxNearbySpots = 2_000,
        uploadUrlTtl = Duration.ofMinutes(5),
        maxUploadBytes = 10L * 1024 * 1024,
        allowedContentTypes = setOf("image/jpeg"),
    )

    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxed = true)

    private val service = CommunityQueryService(queryPort, spotLookup, eventPublisher, policy)

    private fun <T> emptyPage() = CursorPage<T>(
        edges = emptyList(),
        pageInfo = CursorPageInfo(false, false, null, null),
        totalCount = null,
    )

    private fun givenBlocked(vararg ids: Long) {
        every { queryPort.findBlockedMemberIds(memberId) } returns ids.toSet()
    }

    /** 대상·좌표를 주지 않은 전체 피드 조회. 개별 테스트가 필요한 인자만 덮어쓴다. */
    private fun query(
        spotId: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusMeters: Int = 20_000,
        category: TargetCategory? = null,
        sort: PostSortOrder = PostSortOrder.LATEST,
        first: Int = 20,
    ) = PostsQuery(
        spotId = spotId,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        category = category,
        tag = null,
        sort = sort,
        first = first,
        after = null,
    )

    private fun captureSearch(): CapturingSlot<PostsSearch> {
        val search = slot<PostsSearch>()
        every { queryPort.findPosts(capture(search)) } returns emptyPage()
        return search
    }

    // ---------- 피드 범위 ----------

    @Test
    fun `장소를 주면 그 장소 하나로 좁힌다`() {
        givenBlocked()
        val search = captureSearch()
        service.posts(query(spotId = 42L), memberId)

        assertEquals(listOf(42L), search.captured.spotIds)
    }

    @Test
    fun `장소도 좌표도 없으면 장소로 좁히지 않는다 - 전체 피드다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId)

        assertNull(search.captured.spotIds)
        // 대상 조회 자체를 하지 않는다.
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `좌표를 주면 반경 안의 장소로 좁힌다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) } returns listOf(7L, 8L)
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0), memberId)

        assertEquals(listOf(7L, 8L), search.captured.spotIds)
    }

    @Test
    fun `좌표가 하나만 오면 좌표를 안 준 것으로 본다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5), memberId)

        assertNull(search.captured.spotIds)
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `장소와 좌표가 함께 오면 장소가 이긴다`() {
        givenBlocked()
        val search = captureSearch()
        service.posts(query(spotId = 42L, latitude = 37.5, longitude = 127.0), memberId)

        assertEquals(listOf(42L), search.captured.spotIds)
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) }
    }

    // ---------- 상단 칩 필터 ----------

    @Test
    fun `상단 추천 칩은 조회 조건으로 그대로 넘어간다`() {
        // 카테고리는 이제 **글이 직접 들고 있다** — spot에서 유도하지 않으므로
        // 장소 없는 글도 칩에 걸린다.
        givenBlocked()
        val search = captureSearch()

        service.posts(query(category = TargetCategory.RESTAURANT), memberId)

        assertEquals(TargetCategory.RESTAURANT, search.captured.category)
        // 카테고리를 알아내려고 장소를 물을 이유가 없다.
        verify(exactly = 0) { spotLookup.findSummaries(any()) }
    }

    @Test
    fun `칩을 고르지 않으면 카테고리 조건이 없다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId)

        assertNull(search.captured.category)
    }

    // ---------- 모더레이션 ----------

    @Test
    fun `피드는 양방향 차단 관계를 조회 컨텍스트에 담는다`() {
        givenBlocked(11L, 12L)
        val search = captureSearch()

        service.posts(query(), memberId)

        assertEquals(setOf(11L, 12L), search.captured.viewer.blockedMemberIds)
        assertEquals(memberId, search.captured.viewer.memberId)
    }

    @Test
    fun `단건 조회도 같은 차단 컨텍스트를 거친다 - 딥링크로 우회되지 않는다`() {
        givenBlocked(11L)
        val viewer = slot<ViewerContext>()
        every { queryPort.findPost(any(), capture(viewer)) } returns null

        service.post(99L, memberId)

        assertEquals(setOf(11L), viewer.captured.blockedMemberIds)
    }

    @Test
    fun `댓글 조회도 같은 차단 컨텍스트를 거친다`() {
        givenBlocked(11L)
        val search = slot<PostCommentsSearch>()
        every { queryPort.findComments(capture(search)) } returns emptyPage()

        service.comments(PostCommentsQuery(1L, 20, null), memberId)

        assertEquals(setOf(11L), search.captured.viewer.blockedMemberIds)
    }

    // ---------- 상한 ----------

    @Test
    fun `페이지 크기는 상한으로 절삭된다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(first = 999), memberId)

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `페이지 크기 하한은 1이다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(first = 0), memberId)

        assertEquals(1, search.captured.first)
    }

    @Test
    fun `반경은 상한으로 절삭된다 - 오류가 아니다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) } returns listOf(1L)
        every { queryPort.findPosts(any()) } returns emptyPage()

        service.posts(query(latitude = 37.5, longitude = 127.0, radiusMeters = 999_999), memberId)

        verify { spotLookup.findIdsWithin(any(), any(), 100_000, 2_000) }
    }

    @Test
    fun `차단 목록은 본인 것만 조회한다`() {
        val search = slot<BlockedUsersSearch>()
        every { queryPort.findBlockedUsers(capture(search)) } returns emptyPage()

        service.blockedUsers(BlockedUsersQuery(20, null), memberId)

        assertEquals(memberId, search.captured.memberId)
    }

    // ---------- 상단 칩 구성 ----------

    @Test
    fun `상단 칩은 앱마다 다른 구성을 내려준다`() {
        val trip = AppKey("k-trip-spot")
        val demo = AppKey("demo-app")
        every { queryPort.findChips(trip, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT, 0),
            CommunityChipView(TargetCategory.FESTIVAL_EVENT, 1),
        )
        every { queryPort.findChips(demo, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.SHOPPING, 0),
        )

        assertEquals(2, service.communityChips(trip, ContentLanguage.KO).size)
        assertEquals(
            listOf(TargetCategory.SHOPPING),
            service.communityChips(demo, ContentLanguage.KO).map { it.category },
        )
    }

    @Test
    fun `칩 구성은 언어별로 갈린다 - 여행코스는 국문, 교통은 외국어 전용이다`() {
        val trip = AppKey("k-trip-spot")
        every { queryPort.findChips(trip, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT, 0),
            CommunityChipView(TargetCategory.TRAVEL_COURSE, 1),
        )
        every { queryPort.findChips(trip, ContentLanguage.EN) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT, 0),
            CommunityChipView(TargetCategory.TRANSPORT, 1),
        )

        val ko = service.communityChips(trip, ContentLanguage.KO).map { it.category }
        val en = service.communityChips(trip, ContentLanguage.EN).map { it.category }

        // 없는 카테고리를 칩으로 띄우면 눌러도 결과가 비어 있다.
        assertTrue(TargetCategory.TRAVEL_COURSE in ko)
        assertTrue(TargetCategory.TRANSPORT !in ko)
        assertTrue(TargetCategory.TRANSPORT in en)
        assertTrue(TargetCategory.TRAVEL_COURSE !in en)
    }

    @Test
    fun `구성이 없는 앱은 빈 목록이며 오류가 아니다`() {
        val unknown = AppKey("unknown-app")
        every { queryPort.findChips(unknown, ContentLanguage.KO) } returns emptyList()

        assertTrue(service.communityChips(unknown, ContentLanguage.KO).isEmpty())
    }

    // ---------- 정렬 ----------

    @Test
    fun `카테고리는 spot 조회 단계에서도 걸린다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) } returns listOf(7L)
        captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0, category = TargetCategory.RESTAURANT), memberId)

        // 상한(2000)이 관련 없는 spot으로 채워지면 맛집 칩을 눌러도 결과가 비어 보인다.
        verify { spotLookup.findIdsWithin(any(), any(), any(), any(), "RESTAURANT") }
    }

    @Test
    fun `좌표가 없으면 거리순 요청도 최신순으로 떨어진다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(sort = PostSortOrder.DISTANCE), memberId)

        // 거리를 잴 기준이 없다. 오류 대신 정렬만 바꿔 화면은 뜨게 한다.
        assertEquals(PostSortOrder.LATEST, search.captured.sort)
    }

    @Test
    fun `좌표가 있으면 거리순이 그대로 전달된다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any(), any()) } returns listOf(7L, 8L)
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0, sort = PostSortOrder.DISTANCE), memberId)

        assertEquals(PostSortOrder.DISTANCE, search.captured.sort)
        // 목록의 순서가 곧 거리 순서다 — 어댑터가 이 순서를 정렬에 그대로 쓴다.
        assertEquals(listOf(7L, 8L), search.captured.spotIds)
    }

    // ---------- 조회수 ----------

    @Test
    fun `글 단건을 보면 조회 이벤트가 발행된다`() {
        givenBlocked()
        every { queryPort.findPost(99L, any()) } returns postView(id = 99L)

        service.post(99L, memberId)

        // 증가 자체는 리스너가 비동기로 한다 — 조회 응답이 UPDATE를 기다리지 않는다.
        verify(exactly = 1) { eventPublisher.publish(PostViewed(99L)) }
    }

    @Test
    fun `피드 목록은 조회수를 올리지 않는다`() {
        givenBlocked()
        every { queryPort.findPosts(any()) } returns emptyPage()

        service.posts(query(), memberId)

        // 카드가 스쳐 지나가는 것은 조회가 아니다. 세면 스크롤 한 번에 수십이 올라
        // 숫자가 의미를 잃는다.
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    @Test
    fun `차단이나 숨김으로 못 본 글은 조회수를 올리지 않는다`() {
        givenBlocked(11L)
        // 걸러져 null이 나온 경우다.
        every { queryPort.findPost(99L, any()) } returns null

        service.post(99L, memberId)

        // 보지 못한 것을 조회로 셀 수는 없다.
        verify(exactly = 0) { eventPublisher.publish(any()) }
    }

    // ---------- 지연 필드 ----------
    //
    // 태그·사진·카테고리·장소·좋아요는 각각 다른 테이블에 있어 **글 목록과 함께 읽지
    // 않는다.** 클라이언트가 그 필드를 고른 경우에만 DataLoader가 조회를 부른다.
    // 여기를 놓치면 `{ id title }`만 물어도 조회가 다섯 번 붙어 지연 필드가 무의미해진다.

    @Test
    fun `피드 조회 자체는 지연 필드를 하나도 읽지 않는다`() {
        givenBlocked()
        every { queryPort.findPosts(any()) } returns emptyPage()

        service.posts(query(), memberId)

        // 하나라도 불리면 그 필드를 고르지 않은 클라이언트가 값을 치르게 된다.
        verify(exactly = 0) { queryPort.findTagsOf(any()) }
        verify(exactly = 0) { queryPort.findPhotoKeysOf(any()) }
        verify(exactly = 0) { queryPort.findCategoriesOf(any()) }
        verify(exactly = 0) { queryPort.findSpotIdsOf(any()) }
        verify(exactly = 0) { queryPort.findLikedPostIds(any(), any()) }
    }

    @Test
    fun `단건 조회도 지연 필드를 읽지 않는다`() {
        givenBlocked()
        every { queryPort.findPost(42L, any()) } returns null

        service.post(42L, memberId)

        verify(exactly = 0) { queryPort.findTagsOf(any()) }
        verify(exactly = 0) { queryPort.findCategoriesOf(any()) }
        verify(exactly = 0) { queryPort.findSpotIdsOf(any()) }
    }

    @Test
    fun `지연 필드는 여러 글을 한 번에 읽는다`() {
        val postIds = listOf(1L, 2L, 3L)
        every { queryPort.findTagsOf(postIds) } returns mapOf(1L to listOf("야경"))

        val tags = service.postTags(postIds)

        // 글마다 부르면 피드 한 페이지에 쿼리가 스무 번 붙는다(N+1).
        verify(exactly = 1) { queryPort.findTagsOf(postIds) }
        assertEquals(listOf("야경"), tags[1L])
        // 값이 없는 글은 키가 없다 — 채우는 것은 호출자(DataLoader)의 몫이다.
        assertNull(tags[2L])
    }

    @Test
    fun `좋아요 판정은 차단 관계를 읽지 않는다`() {
        every { queryPort.findLikedPostIds(memberId, listOf(1L)) } returns setOf(1L)

        service.likedPostIds(listOf(1L), memberId)

        // 차단은 좋아요 여부에 영향을 주지 않는다 — 읽으면 쿼리가 공짜로 한 번 더 붙는다.
        verify(exactly = 0) { queryPort.findBlockedMemberIds(any()) }
    }

    private fun postView(id: Long) = PostView(
        id = id,
        title = "제목",
        body = "본문",
        authorId = 3L,
        likeCount = 0,
        commentCount = 0,
        viewCount = 0,
        ownedByViewer = false,
        hidden = false,
        edited = false,
        createdAt = Instant.parse("2026-08-05T00:00:00Z"),
    )

    private fun summary(spotId: Long, category: TargetCategory?) = PostSpotView(
        id = spotId,
        title = "대상",
        thumbnailImageUrl = "url",
        category = category,
    )
}
