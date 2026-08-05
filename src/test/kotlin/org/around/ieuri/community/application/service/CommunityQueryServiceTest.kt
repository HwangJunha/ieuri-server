package org.around.ieuri.community.application.service

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.application.port.out.query.BlockedUsersSearch
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.port.out.query.PostCommentsSearch
import org.around.ieuri.community.application.port.out.query.PostsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.BlockedUsersQuery
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.application.query.PostCommentsQuery
import org.around.ieuri.community.application.query.PostTargetView
import org.around.ieuri.community.application.query.PostsQuery
import org.around.ieuri.community.domain.vo.AppKey
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Duration
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
    private val blockRepository = mockk<BlockRepositoryPort>()
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

    private val service = CommunityQueryService(queryPort, blockRepository, spotLookup, policy)

    private fun <T> emptyPage() = CursorPage<T>(
        edges = emptyList(),
        pageInfo = CursorPageInfo(false, false, null, null),
        totalCount = null,
    )

    private fun givenBlocked(vararg ids: Long) {
        every { blockRepository.findRelatedMemberIds(memberId) } returns ids.toSet()
    }

    /** 대상·좌표를 주지 않은 전체 피드 조회. 개별 테스트가 필요한 인자만 덮어쓴다. */
    private fun query(
        target: PostTarget? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        radiusMeters: Int = 20_000,
        category: TargetCategory? = null,
        first: Int = 20,
    ) = PostsQuery(
        target = target,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        category = category,
        topic = null,
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
    fun `대상을 주면 그 대상 하나로 좁힌다`() {
        givenBlocked()
        val search = captureSearch()
        val target = PostTarget(PostTargetType.SPOT, 42L)

        service.posts(query(target = target), memberId)

        assertEquals(listOf(target), search.captured.targets)
    }

    @Test
    fun `대상도 좌표도 없으면 대상으로 좁히지 않는다 - 전체 피드다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(), memberId)

        assertNull(search.captured.targets)
        // 대상 조회 자체를 하지 않는다.
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any()) }
    }

    @Test
    fun `좌표를 주면 반경 안의 대상으로 좁힌다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any()) } returns listOf(7L, 8L)
        val search = captureSearch()

        service.posts(query(latitude = 37.5, longitude = 127.0), memberId)

        assertEquals(
            listOf(PostTarget(PostTargetType.SPOT, 7L), PostTarget(PostTargetType.SPOT, 8L)),
            search.captured.targets,
        )
    }

    @Test
    fun `좌표가 하나만 오면 좌표를 안 준 것으로 본다`() {
        givenBlocked()
        val search = captureSearch()

        service.posts(query(latitude = 37.5), memberId)

        assertNull(search.captured.targets)
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any()) }
    }

    @Test
    fun `대상과 좌표가 함께 오면 대상이 이긴다`() {
        givenBlocked()
        val search = captureSearch()
        val target = PostTarget(PostTargetType.SPOT, 42L)

        service.posts(query(target = target, latitude = 37.5, longitude = 127.0), memberId)

        assertEquals(listOf(target), search.captured.targets)
        verify(exactly = 0) { spotLookup.findIdsWithin(any(), any(), any(), any()) }
    }

    // ---------- 상단 칩 필터 ----------

    @Test
    fun `상단 추천 칩은 대상 카테고리로 거른다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any()) } returns listOf(1L, 2L)
        every { spotLookup.findSummaries(any()) } returns mapOf(
            1L to summary(1L, TargetCategory.RESTAURANT),
            2L to summary(2L, TargetCategory.FESTIVAL_EVENT),
        )
        val search = captureSearch()

        service.posts(
            query(latitude = 37.5, longitude = 127.0, category = TargetCategory.RESTAURANT),
            memberId,
        )

        assertEquals(listOf(PostTarget(PostTargetType.SPOT, 1L)), search.captured.targets)
    }

    @Test
    fun `칩을 고르지 않으면 카테고리 조회 자체를 하지 않는다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any()) } returns listOf(1L)
        every { queryPort.findPosts(any()) } returns emptyPage()

        service.posts(query(latitude = 37.5, longitude = 127.0), memberId)

        verify(exactly = 0) { spotLookup.findSummaries(any()) }
    }

    @Test
    fun `카테고리가 없는 대상은 칩 선택 시 제외된다`() {
        givenBlocked()
        every { spotLookup.findIdsWithin(any(), any(), any(), any()) } returns listOf(3L)
        every { spotLookup.findSummaries(any()) } returns mapOf(3L to summary(3L, null))
        val search = captureSearch()

        service.posts(
            query(latitude = 37.5, longitude = 127.0, category = TargetCategory.RESTAURANT),
            memberId,
        )

        assertTrue(search.captured.targets!!.isEmpty())
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
        every { spotLookup.findIdsWithin(any(), any(), any(), any()) } returns listOf(1L)
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

    private fun summary(spotId: Long, category: TargetCategory?) = PostTargetView(
        type = PostTargetType.SPOT,
        id = spotId,
        title = "대상",
        thumbnailImageUrl = "url",
        category = category,
    )
}
