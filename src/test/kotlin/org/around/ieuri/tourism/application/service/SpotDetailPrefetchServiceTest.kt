package org.around.ieuri.tourism.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.exception.UpstreamTooManyRequestsException
import org.around.ieuri.tourism.application.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.policy.SpotDetailPrefetchPolicy
import org.around.ieuri.tourism.application.port.out.client.SpotDetailQuery
import org.around.ieuri.tourism.application.port.out.client.TourInfoPort
import org.around.ieuri.tourism.application.port.out.persistence.SpotDetailUpsertPort
import org.around.ieuri.tourism.application.port.out.query.PrefetchTarget
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPart
import org.around.ieuri.tourism.application.port.out.query.SpotDetailPrefetchTargetQueryPort
import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.support.TaskExecutorAdapter
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 상세 사전 수집의 판단을 고정한다(SPEC-0037). 검증 대상은 **부재와 실패의 구분**, **재시도가
 * 무엇에만 걸리는지**, **한도가 커서를 어떻게 다루는지** 셋이다.
 *
 * executor는 동기 실행기로 갈아 끼운다 — 동시 실행 자체는 Spring의 몫이고, 여기서 확인할 것은
 * 어떤 I/O가 일어나고 무엇이 적재되는가다.
 */
class SpotDetailPrefetchServiceTest {

    private lateinit var targetQueryPort: SpotDetailPrefetchTargetQueryPort
    private lateinit var tourInfoPort: TourInfoPort
    private lateinit var upsertPort: SpotDetailUpsertPort
    private lateinit var service: SpotDetailPrefetchService

    private val policy = SpotDetailPrefetchPolicy(
        refreshAfter = Duration.ofDays(30),
        retryCount = 3,
        // 테스트가 실제로 잠들지 않도록 0으로 둔다. 간격의 증가는 정책이 계산하므로 여기서 재지 않는다.
        retryBaseDelay = Duration.ZERO,
        concurrency = 4,
    )

    @BeforeEach
    fun setUp() {
        targetQueryPort = mockk()
        tourInfoPort = mockk()
        upsertPort = mockk(relaxed = true)
        service = SpotDetailPrefetchService(
            targetQueryPort,
            tourInfoPort,
            upsertPort,
            TaskExecutorAdapter(SyncTaskExecutor()),
            policy,
        )
    }

    @Test
    fun `대상이 없으면 소진으로 보고 커서를 그대로 둔다`() {
        givenTargets(SpotDetailPart.BODY, emptyList())

        val result = service.prefetchNext(SpotDetailPart.BODY, afterSpotId = 7L, limit = 10)

        // processed가 0이어야 Tasklet이 FINISHED로 끝낸다. 커서를 옮기면 재개 지점이 어긋난다.
        assertEquals(0, result.processed)
        assertEquals(7L, result.lastSpotId)
    }

    @Test
    fun `AC-005 - 본문이 없으면 부재 행으로 적재한다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } returns null

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 부재를 적재해야 미확보와 구분되고 조회가 다시 묻지 않는다. NOT NULL 컬럼은 spot 값으로 찬다.
        verify(exactly = 1) {
            upsertPort.saveDetailAbsence(
                1L,
                match { it.title == "경복궁" && it.overview == null && it.thumbnailImageUrl == "https://img/1" },
            )
        }
        verify(exactly = 0) { upsertPort.saveDetail(any(), any()) }
        assertEquals(1, result.empty)
        assertEquals(0, result.failed)
    }

    @Test
    fun `AC-021 - 예외로 끝나면 확보실패이며 부재로 적재하지 않는다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 답을 듣지 못한 것을 "없다"로 적으면 원본에 내용이 있어도 30일간 비어 있게 된다.
        verify(exactly = 0) { upsertPort.saveDetailAbsence(any(), any()) }
        verify(exactly = 0) { upsertPort.saveDetail(any(), any()) }
        assertEquals(1, result.failed)
        assertEquals(0, result.empty)
    }

    @Test
    fun `AC-011 - 네트워크 오류는 재시도한다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 첫 시도 + retryCount회. 마지막 시도의 예외가 그대로 나가 실패로 집계된다.
        verify(exactly = policy.retryCount + 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-012 - 일일 한도는 재시도하지 않는다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamRateLimitedException()

        service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 그날이 끝난 것이라 기다려도 풀리지 않는다. 재시도하면 한도 소진만 앞당긴다.
        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `초당 한도는 재시도 대상이다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamTooManyRequestsException()

        service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 일일 한도와 섞으면 초당 한도 한 번에 그날 수집이 통째로 끝난다.
        verify(exactly = policy.retryCount + 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-014 - 한도에 닿아도 그때까지 확보한 것은 보존된다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L), target(2L)))
        every { tourInfoPort.findDetail(queryOf("c1")) } returns detail()
        every { tourInfoPort.findDetail(queryOf("c2")) } throws UpstreamRateLimitedException()

        val result = service.prefetchNext(SpotDetailPart.BODY, afterSpotId = 0L, limit = 10)

        // 한도는 예외가 아니라 결과다 — 호출자가 그 갈래를 오늘 중단한다.
        assertTrue(result.dailyLimitReached)
        verify(exactly = 1) { upsertPort.saveDetail(1L, any()) }
        // 막힌 건은 실패가 아니라 시도조차 못 한 것이다. 이것까지 더해야 processed와 맞는다.
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        assertEquals(result.processed, result.fetched + result.empty + result.failed + result.skipped)
    }

    @Test
    fun `호출 수는 재시도까지 센다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // 일일 예산은 시도한 장소 수가 아니라 소모한 호출 수로 지켜야 한다 — 재시도를 빼고 세면
        // 실패가 많은 날 예산을 훌쩍 넘긴다.
        assertEquals(policy.retryCount + 1, result.calls)
    }

    @Test
    fun `한도가 없으면 마지막 대상까지 커서를 전진시킨다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L), target(2L)))
        every { tourInfoPort.findDetail(any()) } returns detail()

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        assertFalse(result.dailyLimitReached)
        assertEquals(2L, result.lastSpotId)
        assertEquals(2, result.fetched)
    }

    @Test
    fun `AC-002 - 갈래마다 자기 upstream만 부른다`() {
        givenTargets(SpotDetailPart.GALLERY, listOf(target(1L)))
        every { tourInfoPort.findGallery(any()) } returns listOf(image())

        service.prefetchNext(SpotDetailPart.GALLERY, 0L, 10)

        // 한 갈래가 다른 갈래를 끌고 오면 호출량이 갈래 수만큼 늘고 FR-003의 독립성이 깨진다.
        verify(exactly = 1) { tourInfoPort.findGallery(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { upsertPort.saveGallery(1L, any()) }
    }

    @Test
    fun `이용안내는 값이 없어도 행을 남긴다`() {
        givenTargets(SpotDetailPart.INTRO, listOf(target(1L)))
        every { tourInfoPort.findIntro(any(), any()) } returns null

        val result = service.prefetchNext(SpotDetailPart.INTRO, 0L, 10)

        // null을 그대로 넘겨야 어댑터가 전 컬럼 null 행을 남긴다 — 미적재와 구분되는 유일한 수단이다.
        verify(exactly = 1) { upsertPort.saveIntro(1L, CATEGORY, null) }
        assertEquals(1, result.empty)
    }

    @Test
    fun `반복정보는 카테고리를 함께 넘긴다`() {
        givenTargets(SpotDetailPart.INFO, listOf(target(1L)))
        every { tourInfoPort.findInfoItems(any(), any()) } returns listOf(infoItem())

        service.prefetchNext(SpotDetailPart.INFO, 0L, 10)

        // 저장 키가 (spot_id, category, display_order)라 카테고리가 빠지면 다른 카테고리 행을 덮는다.
        verify(exactly = 1) { upsertPort.saveInfoItems(1L, CATEGORY, any()) }
    }

    private fun givenTargets(part: SpotDetailPart, targets: List<PrefetchTarget>) {
        every { targetQueryPort.findTargets(part, any(), any(), any()) } returns targets
    }

    private fun queryOf(contentId: String) = SpotDetailQuery(contentId, DomainLocale.KO)

    private fun target(spotId: Long) = PrefetchTarget(
        spotId = spotId,
        contentId = "c$spotId",
        language = DomainLocale.KO,
        category = CATEGORY,
        title = "경복궁",
        thumbnailImageUrl = "https://img/$spotId",
        copyright = CopyrightType.TYPE1,
        address = "서울 종로구",
    )

    private fun detail() = SpotDetail(
        title = "경복궁",
        category = CATEGORY,
        thumbnailImageUrl = "https://img/1",
        overview = "조선의 법궁",
        address = "서울 종로구",
        homepage = null,
        tel = null,
        copyright = CopyrightType.TYPE1,
    )

    private fun image() = SpotImage(
        imageUrl = "https://img/g1",
        thumbnailUrl = null,
        caption = null,
        copyright = CopyrightType.TYPE1,
    )

    private fun infoItem() = SpotInfoItem(name = "이용시간", text = "09:00-18:00")

    private companion object {
        val CATEGORY = SpotCategory("TOURIST_SPOT")
    }
}
