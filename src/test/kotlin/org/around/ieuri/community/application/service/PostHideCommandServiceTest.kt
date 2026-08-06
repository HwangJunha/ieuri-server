package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.HidePostCommand
import org.around.ieuri.community.application.exception.PostNotFoundException
import org.around.ieuri.community.application.port.out.persistence.PostHideRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PostModerationPort
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 개별 글 숨김.
 *
 * **자기 화면에서만 감춘다** — 글은 그대로이고 다른 사람에게는 계속 보인다. 신고 누적으로
 * 모두에게 감춰지는 `Post.hidden`과 다른 개념이며, 소유권도 보지 않는다(조치가 아니라
 * 개인 화면 설정이다).
 */
class PostHideCommandServiceTest {

    private val memberId = 7L
    private val postId = 1L

    private val hideRepository = mockk<PostHideRepositoryPort>(relaxed = true)
    private val moderationPort = mockk<PostModerationPort>()

    private val service = PostHideCommandService(hideRepository, moderationPort)

    @Test
    fun `글을 숨기면 기록이 남는다`() {
        every { moderationPort.exists(postId) } returns true

        service.hide(HidePostCommand(postId), memberId)

        verify(exactly = 1) { hideRepository.save(memberId, postId) }
    }

    @Test
    fun `남의 글도 숨길 수 있다 - 소유권을 보지 않는다`() {
        every { moderationPort.exists(postId) } returns true

        // 조치가 아니라 개인 화면 설정이라 작성자 여부를 판정하지 않는다.
        service.hide(HidePostCommand(postId), memberId + 999)

        verify(exactly = 1) { hideRepository.save(memberId + 999, postId) }
    }

    @Test
    fun `이미 숨긴 글을 다시 숨겨도 오류가 아니다`() {
        every { moderationPort.exists(postId) } returns true
        // 저장소의 유일 제약이 멱등을 판정하고 false를 돌려준다.
        every { hideRepository.save(memberId, postId) } returns false

        service.hide(HidePostCommand(postId), memberId)
    }

    @Test
    fun `존재하지 않는 글은 숨길 수 없다`() {
        every { moderationPort.exists(postId) } returns false

        assertFailsWith<PostNotFoundException> { service.hide(HidePostCommand(postId), memberId) }
        verify(exactly = 0) { hideRepository.save(any(), any()) }
    }

    @Test
    fun `숨김을 해제하면 기록이 지워진다`() {
        service.unhide(HidePostCommand(postId), memberId)

        verify(exactly = 1) { hideRepository.delete(memberId, postId) }
    }

    @Test
    fun `해제는 글의 존재를 확인하지 않는다`() {
        // 이미 삭제된 글의 숨김 기록도 지울 수 있어야 목록에 남지 않는다.
        service.unhide(HidePostCommand(postId), memberId)

        verify(exactly = 0) { moderationPort.exists(any()) }
    }

    @Test
    fun `숨기지 않은 글을 해제해도 오류가 아니다`() {
        every { hideRepository.delete(memberId, postId) } returns false

        service.unhide(HidePostCommand(postId), memberId)
    }
}
