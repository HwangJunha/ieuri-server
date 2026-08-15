package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.port.out.query.OrphanPhotoQueryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 피드 사진의 삭제 대상 판정(SPEC-0021 R6). 판정만 하며 스토리지는 건드리지 않는다. */
class OrphanPhotoServiceTest {

    private val queryPort = mockk<OrphanPhotoQueryPort>()
    private val service = OrphanPhotoService(queryPort)

    /** AC6.1 — 피드에만 쓰인 키가 대상으로 분류된다. */
    @Test
    fun `피드에만 쓰인 키가 삭제 대상이다`() {
        every { queryPort.findOrphanPhotoKeys() } returns listOf("images/community/1/a", "images/community/1/b")
        every { queryPort.findKeysUsedByPosts(any()) } returns emptySet()

        val deletable = service.deletableFeedPhotoKeys()

        assertEquals(listOf("images/community/1/a", "images/community/1/b"), deletable)
    }

    /**
     * AC6.2 — **커뮤니티 글이 같은 키를 쓰고 있으면 제외한다.** 업로드 확정 경로를 공유하므로
     * 중복이 성립하고, 지우면 살아 있는 글의 사진이 깨진다.
     */
    @Test
    fun `커뮤니티 글이 쓰는 키는 대상에서 빠진다`() {
        every { queryPort.findOrphanPhotoKeys() } returns
            listOf("images/community/1/a", "images/community/1/shared", "images/community/1/b")
        every { queryPort.findKeysUsedByPosts(any()) } returns setOf("images/community/1/shared")

        val deletable = service.deletableFeedPhotoKeys()

        assertEquals(listOf("images/community/1/a", "images/community/1/b"), deletable)
    }

    /** AC6.3 — 대상이 없으면 빈 목록이고 실패하지 않는다. 조회도 더 하지 않는다. */
    @Test
    fun `피드 사진 키가 없으면 빈 목록이다`() {
        every { queryPort.findOrphanPhotoKeys() } returns emptyList()

        val deletable = service.deletableFeedPhotoKeys()

        assertTrue(deletable.isEmpty())
        verify(exactly = 0) { queryPort.findKeysUsedByPosts(any()) }
    }

    /** 전부 글이 쓰고 있으면 지울 것이 없다 — 빈 목록이지 실패가 아니다. */
    @Test
    fun `모든 키를 글이 쓰고 있으면 대상이 없다`() {
        every { queryPort.findOrphanPhotoKeys() } returns listOf("images/community/1/a")
        every { queryPort.findKeysUsedByPosts(any()) } returns setOf("images/community/1/a")

        assertTrue(service.deletableFeedPhotoKeys().isEmpty())
    }
}
