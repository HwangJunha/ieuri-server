package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.event.SpotDetailWriteBehindListener
import org.around.ieuri.tourism.infrastructure.adapter.out.event.TourismAsyncConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * write-behind 리스너(SPEC-0004 R-14~R-16).
 *
 * **핵심 검증은 "적재가 호출 스레드에서 벗어나는가"다.** Spring 이벤트는 기본이 동기라
 * `@Async`가 없으면 리스너가 요청 스레드에서 실행되고, 그러면 조회 응답이 적재를 기다린다 —
 * write-behind가 이름만 남는다(AC-39). 그래서 executor를 실제로 붙여 스레드를 확인한다.
 */
class SpotDetailWriteBehindListenerTest {

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

    private fun event() = SpotDetailFetched("100", DomainLocale.KO, detail())

    @Test
    fun `전용 executor가 적재를 호출 스레드에서 분리한다(AC-39)`() {
        // @Async 애노테이션 자체는 Spring 프록시가 해석하므로, 여기서는 그 애노테이션이 가리키는
        // executor에 직접 태워 "분리된 스레드에서 돈다"는 계약을 확인한다.
        val executor = TourismAsyncConfig().spotDetailWriteBehindExecutor()
        val listener = SpotDetailWriteBehindListener(store)
        val callerThread = Thread.currentThread().name
        val workerThread = AtomicReference<String>()
        val done = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            workerThread.set(Thread.currentThread().name)
            done.countDown()
        }

        executor.execute { listener.on(event()) }

        assertTrue(done.await(5, TimeUnit.SECONDS), "적재가 수행되지 않았다")
        assertNotEquals(callerThread, workerThread.get(), "적재가 호출 스레드에서 수행됐다")
        assertTrue(
            workerThread.get().startsWith("spot-detail-writer-"),
            "전용 executor가 아니라 다른 스레드에서 실행됐다: ${workerThread.get()}",
        )
    }

    @Test
    fun `적재가 실패해도 예외가 리스너 밖으로 나가지 않는다(AC-40)`() {
        val listener = SpotDetailWriteBehindListener(store)
        every { store.saveDetail(any(), any(), any()) } throws IllegalStateException("제약 위반")

        // 예외가 새면 이 호출 자체가 실패한다. @Async 경계에서 삼켜지더라도, 리스너가 직접
        // 막아야 로그에 원인이 남는다.
        listener.on(event())

        verify(exactly = 1) { store.saveDetail(any(), any(), any()) }
    }

    @Test
    fun `큐가 포화되면 폐기하되 호출자를 막지 않는다(AC-42·AC-43)`() {
        val config = TourismAsyncConfig()
        val executor = config.spotDetailWriteBehindExecutor()
        val block = CountDownLatch(1)
        val occupied = CountDownLatch(2)

        try {
            // 워커 2개를 점유시킨다.
            repeat(2) {
                executor.execute {
                    occupied.countDown()
                    block.await(10, TimeUnit.SECONDS)
                }
            }
            assertTrue(occupied.await(5, TimeUnit.SECONDS))

            // 큐 상한(500)을 넘도록 투입. 초과분은 거부 핸들러가 폐기한다.
            val elapsed = kotlin.system.measureTimeMillis {
                repeat(700) { executor.execute { } }
            }

            assertTrue(config.discardedCount() > 0, "포화 시 폐기가 기록되어야 한다")
            // 폐기 경로가 블로킹이면 이 시간이 크게 늘어난다.
            assertTrue(elapsed < 5_000, "포화 시에도 호출자가 막히면 안 된다 (실제 ${elapsed}ms)")
        } finally {
            block.countDown()
        }
    }
}
