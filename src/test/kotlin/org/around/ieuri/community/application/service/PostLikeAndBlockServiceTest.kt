package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.BlockUserCommand
import org.around.ieuri.community.application.command.LikePostCommand
import org.around.ieuri.community.application.exception.CannotBlockSelfException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostRepositoryPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.domain.aggregate.Post
import org.around.ieuri.community.domain.vo.TargetCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 좋아요 멱등성(R12). */
class PostLikeCommandServiceTest {

    private val memberId = 7L
    private val postId = 1L

    private val likeRepository = mockk<PostLikeRepositoryPort>()
    private val postRepository = mockk<PostRepositoryPort>()
    private val service = PostLikeCommandService(likeRepository, postRepository)

    private fun post(likeCount: Int) = Post.restore(
        id = postId, category = TargetCategory.TOURIST_SPOT,
        spotId = 1L, authorId = 42L, tags = emptyList(),
        title = "제목", body = "본문", photoKeys = emptyList(), likeCount = likeCount, commentCount = 0,
        hidden = false, deletedAt = null, createdAt = Instant.EPOCH,
    )

    @Test
    fun `좋아요하면 1 증가한다`() {
        val target = post(likeCount = 0)
        every { postRepository.findById(postId) } returns target
        every { likeRepository.save(any()) } returns true
        every { postRepository.save(any()) } returns target

        assertEquals(1, service.like(LikePostCommand(postId), memberId))
    }

    @Test
    fun `이미 좋아요한 글에 다시 눌러도 수가 변하지 않는다`() {
        val target = post(likeCount = 1)
        every { postRepository.findById(postId) } returns target
        every { likeRepository.save(any()) } returns false

        assertEquals(1, service.like(LikePostCommand(postId), memberId))
        // 집계를 올리지 않는다 — 저장소가 멱등을 판정했다.
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `좋아요를 취소하면 1 감소한다`() {
        val target = post(likeCount = 1)
        every { postRepository.findById(postId) } returns target
        every { likeRepository.delete(postId, memberId) } returns true
        every { postRepository.save(any()) } returns target

        assertEquals(0, service.unlike(LikePostCommand(postId), memberId))
    }

    @Test
    fun `좋아요하지 않은 글을 취소해도 0 아래로 내려가지 않는다`() {
        val target = post(likeCount = 0)
        every { postRepository.findById(postId) } returns target
        every { likeRepository.delete(postId, memberId) } returns false

        assertEquals(0, service.unlike(LikePostCommand(postId), memberId))
        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `존재하지 않는 글이면 POST_NOT_FOUND다`() {
        every { postRepository.findById(postId) } returns null
        assertFailsWith<PostNotFoundException> { service.like(LikePostCommand(postId), memberId) }
        assertFailsWith<PostNotFoundException> { service.unlike(LikePostCommand(postId), memberId) }
    }
}

/** 차단·해제(R15). */
class BlockCommandServiceTest {

    private val memberId = 7L
    private val targetId = 8L

    private val blockRepository = mockk<BlockRepositoryPort>()
    private val memberLookup = mockk<MemberLookupPort>()
    private val service = BlockCommandService(blockRepository, memberLookup)

    private fun givenTargetExists() {
        every { memberLookup.findAuthor(targetId) } returns
            PostAuthorView(id = targetId, nickname = "상대", profileImageUrl = null)
    }

    @Test
    fun `다른 사용자를 차단할 수 있다`() {
        givenTargetExists()
        every { blockRepository.save(any()) } returns true

        service.block(BlockUserCommand(targetId), memberId)

        verify { blockRepository.save(any()) }
    }

    @Test
    fun `중복 차단은 오류가 아니라 멱등 no-op이다`() {
        givenTargetExists()
        every { blockRepository.save(any()) } returns false

        service.block(BlockUserCommand(targetId), memberId)
    }

    @Test
    fun `자기 자신 차단은 CANNOT_BLOCK_SELF이고 회원 조회를 하지 않는다`() {
        assertFailsWith<CannotBlockSelfException> {
            service.block(BlockUserCommand(memberId), memberId)
        }
        verify(exactly = 0) { memberLookup.findAuthor(any()) }
    }

    @Test
    fun `존재하지 않는 사용자 차단은 MEMBER_NOT_FOUND다`() {
        every { memberLookup.findAuthor(targetId) } returns null
        assertFailsWith<MemberNotFoundException> { service.block(BlockUserCommand(targetId), memberId) }
    }

    @Test
    fun `차단하지 않은 사용자를 해제해도 오류가 아니다`() {
        givenTargetExists()
        every { blockRepository.delete(memberId, targetId) } returns false

        service.unblock(BlockUserCommand(targetId), memberId)
    }
}
