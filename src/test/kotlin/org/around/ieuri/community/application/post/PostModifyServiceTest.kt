package org.around.ieuri.community.application.post

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.community.application.photo.UploadedPhotoService
import org.around.ieuri.community.application.photo.required.ObjectStoragePort
import org.around.ieuri.community.application.post.provided.dto.CreatePostCommand
import org.around.ieuri.community.application.post.provided.dto.EditPostCommand
import org.around.ieuri.community.application.post.provided.dto.PostTargetCommand
import org.around.ieuri.community.application.post.provided.exception.PostNotFoundException
import org.around.ieuri.community.application.post.required.PostRepository
import org.around.ieuri.community.application.shared.exception.SpotNotFoundInLocaleException
import org.around.ieuri.community.application.shared.required.SpotLookupPort
import org.around.ieuri.community.application.shared.required.dto.SpotSummaryView
import org.around.ieuri.community.domain.photo.AttachedPhotos
import org.around.ieuri.community.domain.photo.PhotoNotUploadedException
import org.around.ieuri.community.domain.photo.PhotoObjectKey
import org.around.ieuri.community.domain.post.HiddenPostNotEditableException
import org.around.ieuri.community.domain.post.Post
import org.around.ieuri.community.domain.post.PostSpot
import org.around.ieuri.community.domain.post.PostTags
import org.around.ieuri.community.domain.post.TargetCategory
import org.around.ieuri.community.domain.shared.ContentLanguage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostModifyServiceTest {

    private val memberId = 7L
    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val postRepository = mockk<PostRepository>(relaxUnitFun = true)
    private val spotLookupPort = mockk<SpotLookupPort>()
    private val objectStoragePort = mockk<ObjectStoragePort>()
    private val clockPort = Clock.fixed(now, ZoneOffset.UTC)
    private val service = PostModifyService(postRepository, spotLookupPort, UploadedPhotoService(objectStoragePort), clockPort)

    private val stored = Post(id = 3, authorId = memberId, title = "제목", body = "본문", edited = false, hidden = false, deletedAt = null)
    private val photoKey = PhotoObjectKey.issue(memberId, UUID.randomUUID()).toConfirmed().value

    private fun create(catalogId: Long? = null, photoKeys: List<String> = emptyList()) = CreatePostCommand(
        category = TargetCategory.RESTAURANT,
        catalogId = catalogId,
        tags = listOf("서울"),
        title = "제목",
        body = "본문",
        photoKeys = photoKeys,
    )

    @Test
    fun `장소를 붙이면 경도·위도를 그 순서대로 글에 복사한다`() {
        every { spotLookupPort.findSummary(343259, ContentLanguage.KO) } returns
            SpotSummaryView(id = 1, title = "남산", thumbnailImageUrl = "", category = null, longitude = 127.01, latitude = 37.55)
        every { objectStoragePort.findSize(photoKey) } returns 10
        val spot = slot<PostSpot?>()
        every { postRepository.insert(any(), TargetCategory.RESTAURANT, captureNullable(spot), any(), any()) } returns 9

        assertEquals(9L, service.createPost(create(catalogId = 343259, photoKeys = listOf(photoKey)), memberId, ContentLanguage.KO))
        assertEquals(343259L, spot.captured?.catalogId)
        assertEquals(127.01, spot.captured?.point?.longitude)
        assertEquals(37.55, spot.captured?.point?.latitude)
    }

    @Test
    fun `활성 언어에서 해소되지 않는 장소는 SPOT_NOT_FOUND_IN_LOCALE이고 저장하지 않는다`() {
        every { spotLookupPort.findSummary(any(), any()) } returns null

        assertFailsWith<SpotNotFoundInLocaleException> { service.createPost(create(catalogId = 1), memberId, ContentLanguage.EN) }
        verify(exactly = 0) { postRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `올라가지 않은 사진이면 저장소를 부르기 전에 PHOTO_NOT_UPLOADED다`() {
        every { objectStoragePort.findSize(photoKey) } returns null

        assertFailsWith<PhotoNotUploadedException> { service.createPost(create(photoKeys = listOf(photoKey)), memberId, ContentLanguage.KO) }
        verify(exactly = 0) { postRepository.insert(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `수정에서 null인 항목은 건드리지 않는다`() {
        every { postRepository.findById(3) } returns stored
        every { postRepository.updateContent(any()) } returns true

        service.editPost(EditPostCommand(postId = 3, title = "새 제목", body = "새 본문", photoKeys = null), memberId, ContentLanguage.KO)

        verify { postRepository.updateContent(match { it.title == "새 제목" && it.edited }) }
        verify(exactly = 0) { postRepository.changeCategory(any(), any()) }
        verify(exactly = 0) { postRepository.changeSpot(any(), any()) }
        verify(exactly = 0) { postRepository.replaceTags(any(), any()) }
        verify(exactly = 0) { postRepository.replacePhotos(any(), any()) }
    }

    @Test
    fun `수정에서 준 항목만 바꾸고 장소 변경에 catalogId가 없으면 뗀다`() {
        every { postRepository.findById(3) } returns stored
        every { postRepository.updateContent(any()) } returns true

        service.editPost(
            EditPostCommand(postId = 3, title = "t", body = "b", photoKeys = emptyList(), category = TargetCategory.SHOPPING, changeSpot = true, tags = emptyList()),
            memberId,
            ContentLanguage.KO,
        )

        verify { postRepository.changeCategory(3, TargetCategory.SHOPPING) }
        verify { postRepository.changeSpot(3, null) }
        verify { postRepository.replaceTags(3, PostTags.of(emptyList())) }
        verify { postRepository.replacePhotos(3, AttachedPhotos.of(emptyList(), memberId)) }
    }

    @Test
    fun `읽은 뒤 숨겨져 내용 갱신이 0건이면 수정할 수 없다`() {
        every { postRepository.findById(3) } returns stored
        every { postRepository.updateContent(any()) } returns false

        assertFailsWith<HiddenPostNotEditableException> {
            service.editPost(EditPostCommand(postId = 3, title = "t", body = "b", photoKeys = null, category = TargetCategory.SHOPPING), memberId, ContentLanguage.KO)
        }
        verify(exactly = 0) { postRepository.changeCategory(any(), any()) }
    }

    @Test
    fun `삭제는 시계 시각으로 표시하고 없는 글은 POST_NOT_FOUND다`() {
        every { postRepository.findById(3) } returns stored
        every { postRepository.markDeleted(3, now) } returns true

        service.deletePost(PostTargetCommand(3), memberId)

        verify { postRepository.markDeleted(3, now) }
        every { postRepository.findById(4) } returns null
        assertFailsWith<PostNotFoundException> { service.deletePost(PostTargetCommand(4), memberId) }
    }

    @Test
    fun `새로 누른 좋아요는 수를 올리고 갱신된 수를 돌려준다`() {
        every { postRepository.exists(3) } returns true
        every { postRepository.likeCount(3) } returns 5
        every { postRepository.addLike(3, memberId) } returns true

        assertEquals(5, service.like(PostTargetCommand(3), memberId))
        verify(exactly = 1) { postRepository.increaseLikeCount(3) }
    }

    @Test
    fun `이미 누른 좋아요는 수를 올리지 않는다`() {
        every { postRepository.exists(3) } returns true
        every { postRepository.likeCount(3) } returns 5
        every { postRepository.addLike(3, memberId) } returns false

        service.like(PostTargetCommand(3), memberId)
        verify(exactly = 0) { postRepository.increaseLikeCount(any()) }
    }

    @Test
    fun `좋아요 취소는 실제로 지웠을 때만 수를 내린다`() {
        every { postRepository.exists(3) } returns true
        every { postRepository.likeCount(3) } returns 4
        every { postRepository.removeLike(3, memberId) } returns false

        service.unlike(PostTargetCommand(3), memberId)

        verify(exactly = 0) { postRepository.decreaseLikeCount(any()) }
    }

    @Test
    fun `탈퇴 정리는 작성 글을 도메인 경로로 하나씩 지우고 좋아요가 있던 글마다 수를 내린다`() {
        every { postRepository.findIdsByAuthor(memberId) } returns listOf(3, 4)
        every { postRepository.findById(3) } returns stored
        every { postRepository.findById(4) } returns null
        every { postRepository.markDeleted(3, now) } returns true
        every { postRepository.removeLikesByMember(memberId) } returns listOf(10, 10, 11)
        every { postRepository.removeHidesByMember(memberId) } returns 2

        assertEquals(1 + 3 + 2, service.eraseMemberContent(memberId, now))

        verify(exactly = 2) { postRepository.decreaseLikeCount(10) }
        verify(exactly = 1) { postRepository.decreaseLikeCount(11) }
        verify(exactly = 0) { postRepository.markDeleted(4, any()) }
    }
}
