package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.queue.InMemorySpotDetailWriteQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * write-behind 큐의 **가용성 보장**(SPEC-0004 R-14~R-16). 검증 대상은 적재 결과가 아니라
 * "조회 응답이 적재에 묶이지 않는가"다 — 그것이 이 클래스의 존재 이유이기 때문이다.
 */
class InMemorySpotDetailWriteQueueTest {

    private val store = mockk<SpotDetailStorePort>(relaxed = true)

    private fun detail() = SpotDetail(
        id = "100",
        title = "경복궁",
        category = SpotCategory.TOURIST_SPOT,
        thumbnailImageUrl = "https://img/100.jpg",
        overview = null,
        address = null,
        homepage = null,
        tel = null,
        copyright = CopyrightType.UNSPECIFIED,
    )

    @Test
    fun `적재는 호출 스레드가 아니라 워커에서 수행된다(AC-39)`() {
        val callerThread = Thread.currentThread().name
        val workerThread = arrayOfNulls<String>(1)
        val done = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            workerThread[0] = Thread.currentThread().name
            done.countDown()
        }
        val queue = InMemorySpotDetailWriteQueue(store)

        queue.enqueueDetail("100", DomainLocale.KO, detail())
        assertTrue(done.await(5, TimeUnit.SECONDS), "적재가 수행되지 않았다")

        // 호출 스레드에서 적재하면(CallerRunsPolicy) 응답이 지연된다 — 그것을 배제한다.
        assertTrue(workerThread[0] != callerThread, "적재가 호출 스레드에서 수행됐다")
        queue.shutdown()
    }

    @Test
    fun `적재가 실패해도 호출자에게 예외가 전파되지 않는다(AC-40)`() {
        val attempted = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            attempted.countDown()
            throw IllegalStateException("제약 위반")
        }
        val queue = InMemorySpotDetailWriteQueue(store)

        // 예외가 새면 이 호출 자체가 실패한다.
        queue.enqueueDetail("100", DomainLocale.KO, detail())

        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        queue.shutdown()
    }

    @Test
    fun `종료 후 유입은 조용히 폐기되고 예외를 던지지 않는다(AC-45)`() {
        val queue = InMemorySpotDetailWriteQueue(store)
        queue.shutdown()

        // 종료 중에도 조회 응답 경로는 살아 있어야 하므로 여기서 던지면 안 된다.
        queue.enqueueDetail("100", DomainLocale.KO, detail())
        queue.enqueueGallery("100", DomainLocale.KO, emptyList())
        queue.enqueueIntro("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT, null)
        queue.enqueueInfoItems("100", DomainLocale.KO, SpotCategory.TOURIST_SPOT, emptyList())

        assertEquals(4, queue.discardedCount(), "폐기 건수가 기록되어야 한다(AC-43)")
    }

    @Test
    fun `큐가 포화되면 폐기하되 호출자를 막지 않는다(AC-42·AC-43)`() {
        // 워커를 붙잡아 큐를 채운다. 상한(500)을 넘기면 거부되어야 하며, 그때도 enqueue는
        // 즉시 반환해야 한다 — 블로킹은 R-15(가용성 유지)와 충돌한다.
        val block = CountDownLatch(1)
        val occupied = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            occupied.countDown()
            block.await(10, TimeUnit.SECONDS)
        }
        val queue = InMemorySpotDetailWriteQueue(store)
        try {
            // 워커 2개를 점유시킨다.
            repeat(2) { queue.enqueueDetail("busy", DomainLocale.KO, detail()) }
            assertTrue(occupied.await(5, TimeUnit.SECONDS))

            // 큐 상한(500)을 넘도록 투입. 초과분은 폐기된다.
            val elapsed = kotlin.system.measureTimeMillis {
                repeat(600) { queue.enqueueDetail("flood-$it", DomainLocale.KO, detail()) }
            }

            assertTrue(queue.discardedCount() > 0, "포화 시 폐기가 기록되어야 한다")
            // 폐기 경로가 블로킹이면 이 시간이 크게 늘어난다.
            assertTrue(elapsed < 5_000, "포화 시에도 호출자가 막히면 안 된다 (실제 ${elapsed}ms)")
        } finally {
            block.countDown()
            queue.shutdown()
        }
    }

    @Test
    fun `종료 시 진행 중인 작업을 유한 시간 안에 마무리한다(R-16)`() {
        val started = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            started.countDown()
            Thread.sleep(200) // 진행 중인 작업 흉내
        }
        val queue = InMemorySpotDetailWriteQueue(store)
        queue.enqueueDetail("100", DomainLocale.KO, detail())
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val elapsed = kotlin.system.measureTimeMillis { queue.shutdown() }

        // 무한 대기하지 않는다 — 배포·재시작을 붙잡으면 안 된다.
        assertTrue(elapsed < 11_000, "종료가 유한 시간 안에 끝나야 한다 (실제 ${elapsed}ms)")
        verify(exactly = 1) { store.saveDetail(any(), any(), any()) }
    }
}
