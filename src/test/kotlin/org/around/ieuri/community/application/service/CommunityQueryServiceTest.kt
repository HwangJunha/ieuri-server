package org.around.ieuri.community.application.service

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.context.AppKey
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.event.PostViewed
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.out.client.CategoryConfigPort
import org.around.ieuri.community.application.port.out.event.CommunityEventPublisherPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.MyPostsSearch
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostSpatialQueryPort
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.MyPostsQuery
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostSortOrder
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커뮤니티 조회 서비스.
 * - 차단 컨텍스트를 피드·단건·댓글 세 경로가 모두 거친다.
 * - 피드 범위는 인자 조합(대상 하나 / 좌표 반경 / 전체)이 정한다.
 */
class CommunityQueryServiceTest {

    private val memberId = 7L
    private val language = ContentLanguage.EN

    private val queryPort = mockk<CommunityQueryPort>()
    private val spatialPort = mockk<PostSpatialQueryPort>()

    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        uploadUrlTtl = Duration.ofMinutes(5),
        maxUploadBytes = 10L * 1024 * 1024,
        allowedContentTypes = setOf("image/jpeg"),
    )

    private val eventPublisher = mockk<CommunityEventPublisherPort>(relaxed = true)

    private val categoryConfigPort = mockk<CategoryConfigPort>()

    private val service = CommunityQueryService(
        queryPort,
        spatialPort,
        categoryConfigPort,
        eventPublisher,
        policy,
    )

    private fun <T> emptyPage() = CursorPage<T>(
        edges = emptyList(),
        pageInfo = CursorPageInfo(false, false, null, null),
        totalCount = null,
    )

    private fun givenBlocked(vararg ids: Long) {
        every { queryPort.findBlockedMemberIds(memberId) } returns ids.toSet()
        // 숨긴 글이 없는 것이 기본. 필요한 테스트만 givenHidden으로 덮어쓴다.
        every { queryPort.findHiddenPostIds(memberId) } returns emptySet()
    }

    private fun givenHidden(vararg postIds: Long) {
        every { queryPort.findHiddenPostIds(memberId) } returns postIds.toSet()
    }

    /** 대상·좌표를 주지 않은 전체 피드 조회. 개별 테스트가 필요한 인자만 덮어쓴다. */
    private fun query(
        catalogId: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusMeters: Int = 20_000,
        categories: Set<TargetCategory> = emptySet(),
        sort: PostSortOrder = PostSortOrder.LATEST,
        hiddenByViewer: Boolean = false,
        first: Int = 20,
    ) = PostsQuery(
        catalogId = catalogId,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        categories = categories,
        tag = null,
        sort = sort,
        hiddenByViewer = hiddenByViewer,
        first = first,
        after = null,
    )

    private fun captureSearch(): CapturingSlot<PostsSearch> {
        val search = slot<PostsSearch>()
        every { queryPort.findPosts(capture(search)) } returns emptyPage()
        return search
    }

    // ---------- 피드 범위 ----------

    /** R-23 — 활성 언어가 조회 조건으로 내려간다(조회 뒤에 거르지 않는다). */
    @Test
    fun `활성 언어를 조회 조건으로 내려보낸다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId, ContentLanguage.JA)

        assertEquals(ContentLanguage.JA, search.captured.language)
    }

    /** 언어가 바뀌면 조건도 함께 바뀐다. */
    @Test
    fun `언어가 바뀌면 조회 조건의 언어도 바뀐다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId, ContentLanguage.ZH)

        assertEquals(ContentLanguage.ZH, search.captured.language)
    }

    @Test
    fun `장소를 주면 그 장소 하나로 좁힌다`() {
        givenBlocked()
        val search = captureSearch()

        // 클라이언트가 보낸 카탈로그 id를 그대로 쓴다(SPEC-0019 R-12).
        service.posts(query(catalogId = 420L), memberId, language)

        assertEquals(420L, search.captured.catalogId)
        assertNull(search.captured.area)
    }

    @Test
    fun `장소도 좌표도 없으면 좁히지 않는다 - 전체 피드다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId, language)

        assertNull(search.captured.catalogId)
        assertNull(search.captured.area)
    }

    @Test
    fun `좌표를 주면 반경을 그대로 넘긴다 - spot 목록을 묻지 않는다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0, radiusMeters = 5_000), memberId, language)

        assertEquals(127.0, search.captured.area?.longitude)
        assertEquals(37.5, search.captured.area?.latitude)
        assertEquals(5_000, search.captured.area?.radiusMeters)
    }

    @Test
    fun `좌표가 하나만 오면 좌표를 안 준 것으로 본다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5), memberId, language)

        assertNull(search.captured.area)
    }

    @Test
    fun `장소와 좌표를 함께 주면 둘 다 조건이 된다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(catalogId = 420L, latitude = 37.5, longitude = 127.0), memberId, language)

        // 서로 배타적이지 않다 — 둘 다 걸면 그 장소이면서 반경 안인 글이 나온다.
        assertEquals(420L, search.captured.catalogId)
        assertEquals(127.0, search.captured.area?.longitude)
    }

    @Test
    fun `반경은 상한으로 절삭된다 - 오류가 아니다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0, radiusMeters = 999_999), memberId, language)

        assertEquals(100_000, search.captured.area?.radiusMeters)
    }

    // ---------- 상단 칩 필터 ----------

    @Test
    fun `상단 추천 칩은 조회 조건으로 그대로 넘어간다`() {
        // 카테고리는 글이 직접 들고 있어 장소 없는 글도 칩에 걸린다.
        givenBlocked()
        val search = captureSearch()

        service.posts(query(categories = setOf(TargetCategory.RESTAURANT)), memberId, language)

        assertEquals(setOf(TargetCategory.RESTAURANT), search.captured.categories)
    }

    @Test
    fun `칩을 고르지 않으면 카테고리 조건이 없다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId, language)

        assertTrue(search.captured.categories.isEmpty())
    }

    // ---------- 모더레이션 ----------

    @Test
    fun `피드는 양방향 차단 관계를 조회 컨텍스트에 담는다`() {
        givenBlocked(11L, 12L)
        val search = captureSearch()

        service.posts(query(), memberId, language)

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
        every { queryPort.findPost(1L, any()) } returns postView(id = 1L)
        val search = slot<PostCommentsSearch>()
        every { queryPort.findComments(capture(search)) } returns emptyPage()

        service.comments(PostCommentsQuery(1L, 20, null), memberId)

        assertEquals(setOf(11L), search.captured.viewer.blockedMemberIds)
    }

    /** 지워진 글의 댓글은 읽히지 않는다(부모 글이 지워져도 댓글 행은 남는다). */
    @Test
    fun `못 보는 글의 댓글은 조회하지 않는다`() {
        givenBlocked()
        every { queryPort.findPost(1L, any()) } returns null

        val page = service.comments(PostCommentsQuery(1L, 20, null), memberId)

        assertTrue(page.edges.isEmpty())
        // 저장소를 아예 묻지 않는다 — 존재 여부가 응답 시간으로도 새지 않는다.
        verify(exactly = 0) { queryPort.findComments(any()) }
    }

    /** 차단·숨김으로 걸린 글과 구분되지 않아야 존재 여부가 새지 않는다 — 오류가 아니라 빈 페이지다. */
    @Test
    fun `못 보는 글의 댓글 조회는 오류가 아니다`() {
        givenBlocked()
        every { queryPort.findPost(1L, any()) } returns null

        val page = service.comments(PostCommentsQuery(1L, 20, null), memberId)

        assertFalse(page.pageInfo.hasNextPage)
        assertFalse(page.pageInfo.hasPreviousPage)
    }

    // ---------- 상한 ----------

    @Test
    fun `페이지 크기는 상한으로 절삭된다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(first = 999), memberId, language)

        assertEquals(50, search.captured.first)
    }

    @Test
    fun `페이지 크기 하한은 1이다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(first = 0), memberId, language)

        assertEquals(1, search.captured.first)
    }

    @Test
    fun `차단 목록은 본인 것만 조회한다`() {
        val search = slot<BlockedUsersSearch>()
        every { queryPort.findBlockedUsers(capture(search)) } returns emptyPage()

        service.blockedUsers(BlockedUsersQuery(20, null), memberId)

        assertEquals(memberId, search.captured.memberId)
    }

    // ---------- 내가 쓴 글 (SPEC-0011 R3) ----------

    @Test
    fun `내가 쓴 글은 인증 주체를 작성자로 고정한다`() {
        val search = slot<MyPostsSearch>()
        every { queryPort.findMyPosts(capture(search)) } returns emptyPage()

        service.myPosts(MyPostsQuery(20, null), memberId)

        assertEquals(memberId, search.captured.memberId)
    }

    @Test
    fun `내가 쓴 글은 차단 숨김 집합을 읽지 않는다`() {
        // 자기 글은 숨겼더라도 마이페이지에 보인다 — 숨김 필터 조회가 없다.
        every { queryPort.findMyPosts(any()) } returns emptyPage()

        service.myPosts(MyPostsQuery(20, null), memberId)

        verify(exactly = 0) { queryPort.findBlockedMemberIds(any()) }
        verify(exactly = 0) { queryPort.findHiddenPostIds(any()) }
    }

    @Test
    fun `내가 쓴 글도 상한을 넘는 first는 절삭된다`() {
        val search = slot<MyPostsSearch>()
        every { queryPort.findMyPosts(capture(search)) } returns emptyPage()

        service.myPosts(MyPostsQuery(999, null), memberId)

        assertEquals(policy.maxPageSize, search.captured.first)
    }

    @Test
    fun `내가 쓴 글의 커서는 그대로 전달된다`() {
        val search = slot<MyPostsSearch>()
        every { queryPort.findMyPosts(capture(search)) } returns emptyPage()

        service.myPosts(MyPostsQuery(20, "cursor-1"), memberId)

        assertEquals("cursor-1", search.captured.after)
    }

    @Test
    fun `내가 쓴 글이 없으면 빈 목록과 hasNextPage 거짓이다`() {
        every { queryPort.findMyPosts(any()) } returns emptyPage()

        val page = service.myPosts(MyPostsQuery(20, null), memberId)

        assertTrue(page.edges.isEmpty())
        assertEquals(false, page.pageInfo.hasNextPage)
    }

    // ---------- 상단 칩 구성 ----------

    @Test
    fun `상단 칩은 앱마다 다른 구성을 내려준다`() {
        val trip = AppKey("k-trip-spot")
        val demo = AppKey("demo-app")
        every { categoryConfigPort.findCategories(trip, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT),
            CommunityChipView(TargetCategory.FESTIVAL_EVENT),
        )
        every { categoryConfigPort.findCategories(demo, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.SHOPPING),
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
        every { categoryConfigPort.findCategories(trip, ContentLanguage.KO) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT),
            CommunityChipView(TargetCategory.TRAVEL_COURSE),
        )
        every { categoryConfigPort.findCategories(trip, ContentLanguage.EN) } returns listOf(
            CommunityChipView(TargetCategory.RESTAURANT),
            CommunityChipView(TargetCategory.TRANSPORT),
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
        every { categoryConfigPort.findCategories(unknown, ContentLanguage.KO) } returns emptyList()

        assertTrue(service.communityChips(unknown, ContentLanguage.KO).isEmpty())
    }

    // ---------- 정렬 ----------

    @Test
    fun `지역 피드가 tourism에 spot 목록을 묻지 않는다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0, categories = setOf(TargetCategory.RESTAURANT)), memberId, language)

        assertEquals(setOf(TargetCategory.RESTAURANT), search.captured.categories)
        assertEquals(127.0, search.captured.area?.longitude)
    }

    @Test
    fun `좌표가 없으면 거리순 요청도 최신순으로 떨어진다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(sort = PostSortOrder.DISTANCE), memberId, language)

        // 좌표가 없으면 오류 대신 최신순으로 바꾼다.
        assertEquals(PostSortOrder.LATEST, search.captured.sort)
    }

    @Test
    fun `거리순은 공간 조회 포트로 간다`() {
        givenBlocked()
        val search = slot<PostsSearch>()
        every { spatialPort.findPostsByDistance(capture(search)) } returns emptyPage()

        service.posts(query(latitude = 37.5, longitude = 127.0, sort = PostSortOrder.DISTANCE), memberId, language)

        // PostGIS라 구현 기술이 갈린다 — 포트를 나눠 그 사실을 계약에 드러낸다.
        assertEquals(PostSortOrder.DISTANCE, search.captured.sort)
        assertEquals(127.0, search.captured.area?.longitude)
        verify(exactly = 0) { queryPort.findPosts(any()) }
    }

    @Test
    fun `최신순은 공간 조회 포트를 쓰지 않는다`() {
        givenBlocked()
        captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0), memberId, language)

        verify(exactly = 0) { spatialPort.findPostsByDistance(any()) }
    }

    // ---------- 개별 숨김 ----------

    @Test
    fun `숨긴 글 id가 조회 컨텍스트에 담긴다`() {
        givenBlocked()
        givenHidden(5L, 7L)
        val search = captureSearch()

        service.posts(query(), memberId, language)

        // 필터가 쿼리 조건으로 들어가야 first만큼 채워진다 — 조회 후 걸러내면 모자란다.
        assertEquals(setOf(5L, 7L), search.captured.viewer.hiddenPostIds)
    }

    @Test
    fun `기본은 숨긴 글을 제외한다`() {
        givenBlocked()
        givenHidden(5L)
        val search = captureSearch()

        service.posts(query(), memberId, language)

        assertEquals(false, search.captured.hiddenByViewer)
    }

    @Test
    fun `숨긴 글만 보기는 그대로 전달된다`() {
        givenBlocked()
        givenHidden(5L)
        val search = captureSearch()

        service.posts(query(hiddenByViewer = true), memberId, language)

        // 이 경로는 숨김 필터를 뒤집어야 한다 — 그러지 않으면 해제할 방법이 없다.
        assertEquals(true, search.captured.hiddenByViewer)
        assertEquals(setOf(5L), search.captured.viewer.hiddenPostIds)
    }

    @Test
    fun `단건 조회도 숨김 컨텍스트를 거친다`() {
        givenBlocked()
        givenHidden(99L)
        val viewer = slot<ViewerContext>()
        every { queryPort.findPost(any(), capture(viewer)) } returns null

        service.post(99L, memberId)

        assertEquals(setOf(99L), viewer.captured.hiddenPostIds)
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

        service.posts(query(), memberId, language)

        // 목록 카드 노출은 조회로 세지 않는다.
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
    // 글 목록 조회는 태그·사진·카테고리·장소·좋아요를 읽지 않는다 — 선택 시 DataLoader가 부른다.

    @Test
    fun `피드 조회 자체는 지연 필드를 하나도 읽지 않는다`() {
        givenBlocked()
        every { queryPort.findPosts(any()) } returns emptyPage()

        service.posts(query(), memberId, language)

        // 하나라도 불리면 그 필드를 고르지 않은 클라이언트가 값을 치르게 된다.
        verify(exactly = 0) { queryPort.findTagsOf(any()) }
        verify(exactly = 0) { queryPort.findPhotoKeysOf(any()) }
        verify(exactly = 0) { queryPort.findCategoriesOf(any()) }
        verify(exactly = 0) { queryPort.findCatalogIdsOf(any()) }
        verify(exactly = 0) { queryPort.findLikedPostIds(any(), any()) }
    }

    @Test
    fun `단건 조회도 지연 필드를 읽지 않는다`() {
        givenBlocked()
        every { queryPort.findPost(42L, any()) } returns null

        service.post(42L, memberId)

        verify(exactly = 0) { queryPort.findTagsOf(any()) }
        verify(exactly = 0) { queryPort.findCategoriesOf(any()) }
        verify(exactly = 0) { queryPort.findCatalogIdsOf(any()) }
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
}
