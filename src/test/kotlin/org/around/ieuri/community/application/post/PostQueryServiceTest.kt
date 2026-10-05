package org.around.ieuri.community.application.post

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.shared.CommunityPolicy
import org.around.ieuri.community.application.shared.dto.PostView
import org.around.ieuri.community.application.shared.dto.PostsQuery
import org.around.ieuri.community.application.shared.required.CategoryConfigPort
import org.around.ieuri.community.application.shared.required.CommunityEventPublisherPort
import org.around.ieuri.community.application.shared.required.CommunityQueryPort
import org.around.ieuri.community.application.shared.required.PostSpatialQueryPort
import org.around.ieuri.community.application.shared.required.PostsSearch
import org.around.ieuri.community.application.shared.required.ViewerContext
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.community.domain.shared.PostSortOrder
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostQueryServiceTest {

    private val memberId = 7L
    private val queries = mockk<CommunityQueryPort> {
        every { findBlockedMemberIds(memberId) } returns setOf(9L)
        every { findHiddenPostIds(memberId) } returns setOf(3L)
    }
    private val spatial = mockk<PostSpatialQueryPort>()
    private val events = mockk<CommunityEventPublisherPort>(relaxUnitFun = true)
    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        uploadUrlTtl = Duration.ofMinutes(30),
        maxUploadBytes = 10,
        allowedContentTypes = setOf("image/jpeg"),
    )
    private val service = PostQueryService(queries, spatial, mockk<CategoryConfigPort>(), events, policy)

    private val view = PostView(1, "제목", "본문", authorId = 8, likeCount = 0, commentCount = 0, viewCount = 0, ownedByViewer = false, hidden = false, edited = false, createdAt = Instant.EPOCH)

    private fun query(sort: PostSortOrder, latitude: Double? = null, longitude: Double? = null, radius: Int = 1000, first: Int = 20, tag: String? = null) = PostsQuery(catalogId = null, latitude = latitude, longitude = longitude, radiusMeters = radius, categories = emptySet(), tag = tag, sort = sort, first = first, after = null)

    @Test
    fun `피드는 조회자의 차단·숨김을 조건으로 넘기고 태그를 정규화하며 페이지 크기를 절삭한다`() {
        val search = slot<PostsSearch>()
        every { queries.findPosts(capture(search)) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.LATEST, first = 999, tag = "#Seoul"), memberId, ContentLanguage.KO)

        assertEquals(ViewerContext(memberId, setOf(9L), setOf(3L)), search.captured.viewer)
        assertEquals("seoul", search.captured.normalizedTag)
        assertEquals(policy.maxPageSize, search.captured.first)
        assertNull(search.captured.area)
    }

    @Test
    fun `좌표가 없으면 거리순 요청도 최신순으로 떨어진다`() {
        every { queries.findPosts(any()) } returns cursorPageOf(emptyList(), false, false)

        service.posts(query(PostSortOrder.DISTANCE), memberId, ContentLanguage.KO)

        verify(exactly = 0) { spatial.findPostsByDistance(any()) }
    }

    @Test
    fun `좌표가 있는 거리순은 공간 포트로 가며 경도·위도를 그대로 넘기고 반경 상한을 절삭한다`() {
        val search = slot<PostsSearch>()
        every { spatial.findPostsByDistance(capture(search)) } returns cursorPageOf(emptyList(), false, false)

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
}
