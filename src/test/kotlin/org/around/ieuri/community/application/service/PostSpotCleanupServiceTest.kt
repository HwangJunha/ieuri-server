package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.port.out.persistence.PostSpotCleanupPort
import kotlin.test.Test

/**
 * 사라진 장소를 가리키는 글-장소 연결 정리(SPEC-0023 R11, AC-8·AC-9).
 *
 * 저장소 포트를 스텁으로 두고 핸들러만 검증한다 — 실제 삭제 반영은 DB에서만 관찰 가능하며
 * 인프라 관심사다.
 */
class PostSpotCleanupServiceTest {

    private val cleanupPort = mockk<PostSpotCleanupPort>()
    private val service = PostSpotCleanupService(cleanupPort)

    /** AC-8 — 삭제된 카탈로그를 가리키던 참조에만 정리가 1회 간다. */
    @Test
    fun `삭제된 카탈로그의 연결만 정리하고 정리는 한 번만 호출한다`() {
        val deleted = listOf(10L, 11L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 3L

        service.cleanupDeletedCatalogs(deleted)

        // 넘긴 목록 그대로 한 번만 — 다른 카탈로그(예: 12L)는 인자에 없으므로 건드릴 수 없다.
        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    /**
     * 한 장소를 여러 글이 참조하므로 정리는 여러 행에 걸친다. 단건 가정을 두지 않는다는 것이
     * 목록을 그대로 넘기는 이 설계의 요지다.
     */
    @Test
    fun `여러 카탈로그를 한 번의 호출로 정리한다`() {
        val deleted = listOf(1L, 2L, 3L, 4L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 9L

        service.cleanupDeletedCatalogs(deleted)

        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }

    /** AC-9 — 정리 대상이 없으면 저장소를 건드리지 않고 예외도 내지 않는다. */
    @Test
    fun `정리 대상이 없으면 저장소를 호출하지 않는다`() {
        service.cleanupDeletedCatalogs(emptyList())

        verify(exactly = 0) { cleanupPort.deleteByCatalogIds(any()) }
    }

    /**
     * AC-9 — 가리키는 참조가 하나도 없어 0건이 지워져도 정상이다. 삭제된 장소를 아무 글도
     * 참조하지 않는 것은 흔한 경우이며 오류가 아니다.
     */
    @Test
    fun `지운 연결이 0건이어도 예외가 아니다`() {
        val deleted = listOf(42L)
        every { cleanupPort.deleteByCatalogIds(deleted) } returns 0L

        service.cleanupDeletedCatalogs(deleted)

        verify(exactly = 1) { cleanupPort.deleteByCatalogIds(deleted) }
    }
}
