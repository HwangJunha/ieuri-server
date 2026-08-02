package org.around.ieuri.tourism.infrastructure

import io.mockk.every
import io.mockk.mockk
import org.around.ieuri.tourism.application.event.SpotDetailFetched
import org.around.ieuri.tourism.application.port.out.event.TourismEventPublisherPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailStorePort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.adapter.out.event.SpotDetailWriteBehindListener
import org.around.ieuri.tourism.infrastructure.adapter.out.event.SpringTourismEventPublisher
import org.around.ieuri.tourism.infrastructure.adapter.out.event.TourismAsyncConfig
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **@Async 배선이 실제 Spring 컨텍스트에서 동작하는지** 확인한다(SPEC-0004 AC-39).
 *
 * 이 검증이 따로 필요한 이유: `@Async`는 **프록시가 해석**하므로 리스너를 직접 호출하는 단위
 * 테스트로는 증명되지 않는다. Spring 이벤트는 기본이 **동기**라, `@EnableAsync`가 빠지거나
 * executor 이름이 어긋나면 리스너가 **발행 스레드(= 요청 스레드)에서 그대로 실행**된다.
 * 그러면 조회 응답이 적재를 기다리게 되어 write-behind가 이름만 남는다.
 *
 * 컴파일로는 잡히지 않는 런타임 속성이므로 컨텍스트를 띄워 스레드를 직접 본다.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [TourismAsyncConfig::class, AsyncWriteBehindWiringTest.TestBeans::class])
@Import(SpringTourismEventPublisher::class, SpotDetailWriteBehindListener::class)
class AsyncWriteBehindWiringTest {

    @TestConfiguration
    class TestBeans {
        @Bean
        fun store(): SpotDetailStorePort = mockk(relaxed = true)
    }

    @Autowired
    private lateinit var publisher: TourismEventPublisherPort

    @Autowired
    private lateinit var store: SpotDetailStorePort

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
    fun `이벤트 발행은 요청 스레드를 벗어나 전용 executor에서 처리된다`() {
        val publisherThread = Thread.currentThread().name
        val listenerThread = AtomicReference<String>()
        val done = CountDownLatch(1)
        every { store.saveDetail(any(), any(), any()) } answers {
            listenerThread.set(Thread.currentThread().name)
            done.countDown()
        }

        publisher.publish(SpotDetailFetched("100", DomainLocale.KO, detail()))

        assertTrue(done.await(5, TimeUnit.SECONDS), "리스너가 실행되지 않았다")
        // @Async가 빠지면 이 둘이 같아진다 — 그것이 곧 응답 지연이다.
        assertNotEquals(publisherThread, listenerThread.get(), "리스너가 발행 스레드에서 실행됐다")
        assertTrue(
            listenerThread.get().startsWith("spot-detail-writer-"),
            "전용 executor가 쓰이지 않았다: ${listenerThread.get()}",
        )
    }
}
