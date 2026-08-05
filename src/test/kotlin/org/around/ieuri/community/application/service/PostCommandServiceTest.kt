package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.CreatePostCommand
import org.around.ieuri.community.application.command.DeletePostCommand
import org.around.ieuri.community.application.command.EditPostCommand
import org.around.ieuri.community.application.exception.HiddenPostNotEditableException
import org.around.ieuri.community.application.exception.NotPostOwnerException
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.PostBodyLengthInvalidException
import org.around.ieuri.community.application.exception.PostBodyRequiredException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.exception.PostTitleLengthInvalidException
import org.around.ieuri.community.application.exception.PostTitleRequiredException
import org.around.ieuri.community.application.exception.SpotNotFoundException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.application.query.PostTargetView
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.PostTarget
import org.around.ieuri.community.domain.vo.PostTargetType
import org.around.ieuri.community.domain.vo.PostTopic
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 글 생성·삭제(R1~R5, R19, R24). */
class PostCommandServiceTest {

    private val memberId = 7L
    private val target = PostTarget(PostTargetType.SPOT, 42L)
    private val now = Instant.parse("2026-08-05T00:00:00Z")

    private val postRepository = mockk<PostRepositoryPort>()
    private val spotLookup = mockk<SpotLookupPort>()
    private val storage = mockk<ObjectStoragePort>()
    private val clock = mockk<ClockPort> { every { now() } returns now }

    private val service = PostCommandService(postRepository, spotLookup, storage, clock)

    private fun confirmedKey(owner: Long = memberId) = "images/$owner/${UUID.randomUUID()}"

    private fun givenValidSpotAndAuthor() {
        every { spotLookup.findSummary(target.id) } returns
            PostTargetView(
                type = PostTargetType.SPOT,
                id = target.id,
                title = "경복궁",
                thumbnailImageUrl = "https://img/1.jpg",
                category = null,
            )
    }

    private fun givenSaveEchoesBackWithId() {
        every { postRepository.save(any()) } answers {
            val p = firstArg<Post>()
            Post.restore(
                id = 1L,
                target = p.target,
                authorId = p.authorId,
                topic = p.topic,
                title = p.title,
                body = p.body,
                photoKeys = p.photoKeys,
                likeCount = p.likeCount,
                commentCount = p.commentCount,
                hidden = p.hidden,
                deletedAt = p.deletedAt,
                createdAt = now,
            )
        }
    }

    private fun create(
        body: String = "본문",
        photoKeys: List<String> = emptyList(),
        title: String = "제목",
    ) =
        service.createPost(
            CreatePostCommand(
                target = target, topic = PostTopic.QUESTION,
                title = title, body = body, photoKeys = photoKeys,
            ),
            memberId,
        )

    @Test
    fun `본문 대상 topic이 갖춰지면 글이 생성된다`() {
        givenValidSpotAndAuthor()
        givenSaveEchoesBackWithId()

        assertEquals(1L, create())
    }

    @Test
    fun `빈 제목은 POST_TITLE_REQUIRED다`() {
        assertFailsWith<PostTitleRequiredException> { create(title = "") }
        assertFailsWith<PostTitleRequiredException> { create(title = "   ") }
    }

    @Test
    fun `51자 제목은 POST_TITLE_LENGTH_INVALID다`() {
        assertFailsWith<PostTitleLengthInvalidException> { create(title = "가".repeat(51)) }
    }

    @Test
    fun `제목 검증이 대상 조회보다 먼저다 - 잘못된 입력에 외부 조회를 하지 않는다`() {
        assertFailsWith<PostTitleRequiredException> { create(title = "") }
        verify(exactly = 0) { spotLookup.findSummary(any()) }
    }

    @Test
    fun `빈 본문은 POST_BODY_REQUIRED다`() {
        assertFailsWith<PostBodyRequiredException> { create(body = "") }
        assertFailsWith<PostBodyRequiredException> { create(body = "   ") }
    }

    @Test
    fun `501자 본문은 POST_BODY_LENGTH_INVALID다`() {
        assertFailsWith<PostBodyLengthInvalidException> { create(body = "가".repeat(501)) }
    }

    @Test
    fun `500자 본문은 생성된다`() {
        givenValidSpotAndAuthor()
        givenSaveEchoesBackWithId()
        assertEquals(1L, create(body = "가".repeat(500)))
    }

    @Test
    fun `존재하지 않는 대상이면 SPOT_NOT_FOUND이고 글은 생성되지 않는다`() {
        every { spotLookup.findSummary(target.id) } returns null

        assertFailsWith<SpotNotFoundException> { create() }
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `사진 6장은 TOO_MANY_PHOTOS이고 저장소를 묻지 않는다`() {
        assertFailsWith<TooManyPhotosException> { create(photoKeys = List(6) { confirmedKey() }) }
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `사진 5장은 생성된다`() {
        givenValidSpotAndAuthor()
        givenSaveEchoesBackWithId()
        val keys = List(5) { confirmedKey() }
        every { storage.findSize(any()) } returns 1024

        assertEquals(1L, create(photoKeys = keys))
    }

    @Test
    fun `타인이 발급받은 키는 저장소를 묻지 않고 PHOTO_NOT_UPLOADED다`() {
        givenValidSpotAndAuthor()

        assertFailsWith<PhotoNotUploadedException> {
            create(photoKeys = listOf(confirmedKey(owner = memberId + 1)))
        }
        // 소유권은 도메인 판정이라 포트 호출이 없다.
        verify(exactly = 0) { storage.findSize(any()) }
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `위조된 키는 PHOTO_NOT_UPLOADED다`() {
        givenValidSpotAndAuthor()
        assertFailsWith<PhotoNotUploadedException> { create(photoKeys = listOf("아무거나")) }
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `확인을 거치지 않은 pending 키는 PHOTO_NOT_UPLOADED다`() {
        givenValidSpotAndAuthor()
        assertFailsWith<PhotoNotUploadedException> {
            create(photoKeys = listOf("pending/$memberId/${UUID.randomUUID()}"))
        }
    }

    @Test
    fun `업로드되지 않은 키가 하나라도 있으면 글 전체가 생성되지 않는다`() {
        givenValidSpotAndAuthor()
        val ok1 = confirmedKey()
        val ok2 = confirmedKey()
        val missing = confirmedKey()
        every { storage.findSize(ok1) } returns 1024
        every { storage.findSize(ok2) } returns 1024
        every { storage.findSize(missing) } returns null

        assertFailsWith<PhotoNotUploadedException> { create(photoKeys = listOf(ok1, ok2, missing)) }
        // 부분 생성은 없다.
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `작성자는 자신의 글을 삭제할 수 있다`() {
        val post = Post.restore(
            id = 1L, target = target, authorId = memberId, topic = PostTopic.QUESTION,
            title = "제목", body = "본문", photoKeys = emptyList(), likeCount = 0, commentCount = 0,
            hidden = false, deletedAt = null, createdAt = now,
        )
        every { postRepository.findById(1L) } returns post
        every { postRepository.save(any()) } returns post

        service.deletePost(DeletePostCommand(1L), memberId)

        assertTrue(post.deleted)
    }

    @Test
    fun `타인의 글 삭제는 NOT_POST_OWNER이고 글은 삭제되지 않는다`() {
        val post = Post.restore(
            id = 1L, target = target, authorId = memberId, topic = PostTopic.QUESTION,
            title = "제목", body = "본문", photoKeys = emptyList(), likeCount = 0, commentCount = 0,
            hidden = false, deletedAt = null, createdAt = now,
        )
        every { postRepository.findById(1L) } returns post

        assertFailsWith<NotPostOwnerException> { service.deletePost(DeletePostCommand(1L), memberId + 1) }
        assertTrue(!post.deleted)
        verify(exactly = 0) { postRepository.save(any()) }
    }

    private fun storedPost(hidden: Boolean = false, authorId: Long = memberId) = Post.restore(
        id = 1L, target = target, authorId = authorId, topic = PostTopic.QUESTION,
        title = "원래 제목", body = "원래 본문", photoKeys = emptyList(),
        likeCount = 0, commentCount = 0, hidden = hidden, deletedAt = null, createdAt = now,
    )

    @Test
    fun `본인 글의 제목과 본문을 수정할 수 있다`() {
        val post = storedPost()
        every { postRepository.findById(1L) } returns post
        every { postRepository.save(any()) } returns post

        service.editPost(EditPostCommand(1L, "새 제목", "새 본문"), memberId)

        assertEquals("새 제목", post.title)
        assertEquals("새 본문", post.body)
        assertTrue(post.edited)
    }

    @Test
    fun `타인의 글 수정은 NOT_POST_OWNER이고 저장하지 않는다`() {
        every { postRepository.findById(1L) } returns storedPost(authorId = memberId + 1)

        assertFailsWith<NotPostOwnerException> {
            service.editPost(EditPostCommand(1L, "제목", "본문"), memberId)
        }
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `숨겨진 글 수정은 POST_NOT_EDITABLE이고 내용이 그대로다`() {
        val post = storedPost(hidden = true)
        every { postRepository.findById(1L) } returns post

        assertFailsWith<HiddenPostNotEditableException> {
            service.editPost(EditPostCommand(1L, "무해한 제목", "무해한 본문"), memberId)
        }
        assertEquals("원래 본문", post.body)
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `수정도 제목과 본문 길이를 서로 다른 코드로 거부한다`() {
        assertFailsWith<PostTitleRequiredException> { service.editPost(EditPostCommand(1L, " ", "본문"), memberId) }
        assertFailsWith<PostTitleLengthInvalidException> {
            service.editPost(EditPostCommand(1L, "가".repeat(51), "본문"), memberId)
        }
        assertFailsWith<PostBodyRequiredException> { service.editPost(EditPostCommand(1L, "제목", " "), memberId) }
        // 형식 검증이 조회보다 먼저다.
        verify(exactly = 0) { postRepository.findById(any()) }
    }

    @Test
    fun `존재하지 않는 글 수정은 POST_NOT_FOUND다`() {
        every { postRepository.findById(1L) } returns null
        assertFailsWith<PostNotFoundException> {
            service.editPost(EditPostCommand(1L, "제목", "본문"), memberId)
        }
    }

    @Test
    fun `이미 삭제된 글의 재삭제는 POST_NOT_FOUND다`() {
        every { postRepository.findById(1L) } returns null
        assertFailsWith<PostNotFoundException> { service.deletePost(DeletePostCommand(1L), memberId) }
    }
}
