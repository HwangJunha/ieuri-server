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
import org.around.ieuri.community.application.port.out.persistence.PostCounterPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import org.around.ieuri.community.application.port.out.query.CommunityQueryPort
import org.around.ieuri.community.application.query.PostAuthorView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 좋아요 멱등성(R12).
 *
 * **애그리게이트를 복원하지 않는다** — 좋아요 수 하나에 태그·사진까지 읽을 이유가 없고,
 * 읽고 고쳐 쓰면 동시 요청이 서로의 증가를 덮어쓴다. 계수는 DB가 원자적으로 올리고
 * 갱신값은 읽기 포트로 다시 조회한다(CQRS).
 */
class PostLikeCommandServiceTest {

    private val memberId = 7L
    private val postId = 1L

    private val likeRepository = mockk<PostLikeRepositoryPort>()
    private val counterPort = mockk<PostCounterPort>(relaxed = true)
    private val moderationPort = mockk<PostModerationPort>()
    private val queryPort = mockk<CommunityQueryPort>()
    private val service = PostLikeCommandService(
        likeRepository, counterPort, moderationPort, queryPort,
    )

    private fun givenPostExists() {
        every { moderationPort.exists(postId) } returns true
    }

    /** 커맨드가 계수를 올린 **뒤** 읽는 값이다. */
    private fun givenLikeCountAfterUpdate(count: Int) {
        every { queryPort.findLikeCount(postId) } returns count
    }

    @Test
    fun `좋아요하면 계수를 원자적으로 올리고 갱신값을 돌려준다`() {
        givenPostExists()
        every { likeRepository.save(any()) } returns true
        givenLikeCountAfterUpdate(1)

        assertEquals(1, service.like(LikePostCommand(postId), memberId))

        verify(exactly = 1) { counterPort.increaseLikeCount(postId) }
    }

    @Test
    fun `이미 좋아요한 글에 다시 눌러도 계수를 올리지 않는다`() {
        givenPostExists()
        // 저장소의 유일 제약이 멱등을 판정한다.
        every { likeRepository.save(any()) } returns false
        givenLikeCountAfterUpdate(1)

        assertEquals(1, service.like(LikePostCommand(postId), memberId))

        verify(exactly = 0) { counterPort.increaseLikeCount(any()) }
    }

    @Test
    fun `좋아요를 취소하면 계수를 내린다`() {
        givenPostExists()
        every { likeRepository.delete(postId, memberId) } returns true
        givenLikeCountAfterUpdate(0)

        assertEquals(0, service.unlike(LikePostCommand(postId), memberId))

        verify(exactly = 1) { counterPort.decreaseLikeCount(postId) }
    }

    @Test
    fun `좋아요하지 않은 글을 취소하면 계수를 건드리지 않는다`() {
        givenPostExists()
        every { likeRepository.delete(postId, memberId) } returns false
        givenLikeCountAfterUpdate(0)

        assertEquals(0, service.unlike(LikePostCommand(postId), memberId))

        verify(exactly = 0) { counterPort.decreaseLikeCount(any()) }
    }

    @Test
    fun `존재 확인에 애그리게이트를 복원하지 않는다`() {
        givenPostExists()
        every { likeRepository.save(any()) } returns true
        givenLikeCountAfterUpdate(1)

        service.like(LikePostCommand(postId), memberId)

        // exists 한 번이면 충분하다 — 복원하면 태그·사진·카테고리·장소까지 읽는다.
        verify(exactly = 1) { moderationPort.exists(postId) }
    }

    @Test
    fun `존재하지 않는 글이면 POST_NOT_FOUND다`() {
        every { moderationPort.exists(postId) } returns false

        assertFailsWith<PostNotFoundException> { service.like(LikePostCommand(postId), memberId) }
        assertFailsWith<PostNotFoundException> { service.unlike(LikePostCommand(postId), memberId) }
        // 없는 글에 좋아요 행을 만들지 않는다.
        verify(exactly = 0) { likeRepository.save(any()) }
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
