package org.around.ieuri.community.application.post

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.event.LocationAccessed
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.block.provided.BlockQueryUseCase
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.post.provided.dto.PostsQuery
import org.around.ieuri.community.application.post.required.CategoryConfigPort
import org.around.ieuri.community.application.post.required.PostEventPublisherPort
import org.around.ieuri.community.application.post.required.PostQueryPort
import org.around.ieuri.community.application.post.required.dto.PostsSearch
import org.around.ieuri.community.application.shared.PagePolicy
import org.around.ieuri.community.application.shared.required.dto.ViewerContext
import org.around.ieuri.community.domain.post.PostSortOrder
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostQueryServiceTest {

    private val memberId = 7L
    private val queries = mockk<PostQueryPort> {
        every { findHiddenPostIds(memberId) } returns setOf(3L)
    }
    private val blocks = mockk<BlockQueryUseCase> {
        every { blockedMemberIds(memberId) } returns setOf(9L)
    }
    private val events = mockk<PostEventPublisherPort>(relaxUnitFun = true)
    private val pagePolicy = PagePolicy(maxPageSize = 50)
    private val policy = PostPolicy(maxRadiusMeters = 100_000)
    private val service = PostQueryService(queries, mockk<CategoryConfigPort>(), blocks, events, pagePolicy, policy)

    private val view = PostView(1, "제목", "본문", authorId = 8, likeCount = 0, commentCount = 0, viewCount = 0, ownedByViewer = false, hidden = false, edited = false, createdAt = Instant.EPOCH)

    private fun query(sort: PostSortOrder, latitude: Double? = null, longitude: Double? = null, radius: Int = 1000, first: Int = 20, tag: String? = null) = PostsQuery(catalogId = null, latitude = latitude, longitude = longitude, radiusMeters = radius, categories = emptySet(), tag = tag, sort = sort, first = first, after = null)

    @Test
    fun `피드는 조회자의 차단·숨김을 조건으로 넘기고 태그를 정규화하며 페이지 크기를 절삭한다`() {
        val search = slot<PostsSearch>()
        every { queries.findPosts(capture(search)) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.LATEST, first = 999, tag = "#Seoul"), memberId, ContentLanguage.KO)

        assertEquals(ViewerContext(memberId, setOf(9L), setOf(3L)), search.captured.viewer)
        assertEquals("seoul", search.captured.normalizedTag)
        assertEquals(pagePolicy.maxPageSize, search.captured.first)
        assertNull(search.captured.area)
    }

    @Test
    fun `좌표가 없으면 거리순 요청도 최신순으로 떨어진다`() {
        every { queries.findPosts(any()) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.DISTANCE), memberId, ContentLanguage.KO)

        verify(exactly = 0) { queries.findPostsByDistance(any()) }
    }

    @Test
    fun `좌표가 있는 거리순은 공간 포트로 가며 경도·위도를 그대로 넘기고 반경 상한을 절삭한다`() {
        val search = slot<PostsSearch>()
        every { queries.findPostsByDistance(capture(search)) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.DISTANCE, latitude = 37.5, longitude = 127.0, radius = 999_999), memberId, ContentLanguage.KO)

        assertEquals(127.0, search.captured.area?.longitude)
        assertEquals(37.5, search.captured.area?.latitude)
        assertEquals(policy.maxRadiusMeters, search.captured.area?.radiusMeters)
    }

    @Test
    fun `단건은 본 글일 때만 조회 이벤트를 낸다`() {
        every { queries.findPost(1, any()) } returns view
        every { queries.findPost(2, any()) } returns null

        service.post(1, memberId)
        service.post(2, memberId)

        verify(exactly = 1) { events.publish(PostViewed(1)) }
        verify(exactly = 0) { events.publish(PostViewed(2)) }
    }

    @Test
    fun `좌표를 보낸 피드 조회만 위치 조회로 남긴다`() {
        every { queries.findPosts(any()) } returns cursorPageOf(emptyList(), false, false)
        every { queries.findPostsByDistance(any()) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.LATEST), memberId, ContentLanguage.KO)
        service.posts(query(PostSortOrder.LATEST, latitude = 37.5), memberId, ContentLanguage.KO)
        verify(exactly = 0) { events.publish(any<LocationAccessed>()) }

        service.posts(query(PostSortOrder.DISTANCE, latitude = 37.5, longitude = 127.0), memberId, ContentLanguage.KO)
        verify(exactly = 1) { events.publish(LocationAccessed(memberId, "posts")) }
    }

    @Test
    fun `노출 판정은 단건과 같은 필터를 거치고 조회 이벤트를 내지 않는다`() {
        val viewer = slot<ViewerContext>()
        every { queries.findPost(1, capture(viewer)) } returns view
        every { queries.findPost(2, any()) } returns null

        assertTrue(service.isVisible(1, memberId))
        assertFalse(service.isVisible(2, memberId))
        assertEquals(ViewerContext(memberId, setOf(9L), setOf(3L)), viewer.captured)
        verify(exactly = 0) { events.publish(any()) }
    }
}
