package org.around.ieuri.tourism.application.spot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.shared.exception.UpstreamRateLimitedException
import org.around.ieuri.tourism.application.shared.exception.UpstreamUnavailableException
import org.around.ieuri.tourism.application.spot.provided.SpotCommandUseCase
import org.around.ieuri.tourism.application.spot.provided.SpotPrefetchQueryUseCase
import org.around.ieuri.tourism.application.spot.required.PrefetchTarget
import org.around.ieuri.tourism.application.spot.required.SpotDetailPart
import org.around.ieuri.tourism.application.spot.required.TourInfoPort
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 상세 사전 수집(SPEC-0037) — 부재와 실패의 구분, 실패 시 재시도 없음, 일일 한도 전파를 본다.
 * - `callsPerSecond`를 크게 두어 유량 제어 대기를 없앤다.
 */
class SpotDetailPrefetchServiceTest {

    private lateinit var targetQueryPort: SpotPrefetchQueryUseCase
    private lateinit var tourInfoPort: TourInfoPort
    private lateinit var upsertPort: SpotCommandUseCase
    private lateinit var service: SpotDetailPrefetchService

    private val policy = SpotDetailPrefetchPolicy(refreshAfter = Duration.ofDays(30), callsPerSecond = 1000)

    @BeforeEach
    fun setUp() {
        targetQueryPort = mockk()
        tourInfoPort = mockk()
        upsertPort = mockk(relaxed = true)
        service = SpotDetailPrefetchService(targetQueryPort, tourInfoPort, upsertPort, policy)
    }

    @Test
    fun `대상이 없으면 소진으로 보고 커서를 그대로 둔다`() {
        givenTargets(SpotDetailPart.BODY, emptyList())

        val result = service.prefetchNext(SpotDetailPart.BODY, afterSpotId = 7L, limit = 10)

        // processed가 0이어야 호출자가 소진으로 판정한다.
        assertEquals(0, result.processed)
        assertEquals(7L, result.lastSpotId)
    }

    @Test
    fun `AC-005 - 본문이 없으면 부재 행으로 적재한다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } returns null

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        // NOT NULL 컬럼은 spot 값으로 찬다.
        verify(exactly = 1) {
            upsertPort.saveDetailAbsence(
                1L,
                match { it.title == "경복궁" && it.overview == null && it.thumbnailImageUrl == "https://img/1" },
            )
        }
        verify(exactly = 0) { upsertPort.saveDetail(any(), any()) }
        assertEquals(0, result.failed)
    }

    @Test
    fun `AC-021 - 예외로 끝나면 확보실패이며 아무것도 적재하지 않는다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        verify(exactly = 0) { upsertPort.saveDetailAbsence(any(), any()) }
        verify(exactly = 0) { upsertPort.saveDetail(any(), any()) }
        assertEquals(1, result.failed)
        assertEquals(1, result.processed)
    }

    @Test
    fun `AC-011 - 실패해도 그 자리에서 다시 시도하지 않는다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamUnavailableException("boom")

        service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        verify(exactly = 1) { tourInfoPort.findDetail(any()) }
    }

    @Test
    fun `AC-018 - 일일 한도는 그대로 올려 호출자가 갈래를 멈추게 한다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L)))
        every { tourInfoPort.findDetail(any()) } throws UpstreamRateLimitedException()

        assertFailsWith<UpstreamRateLimitedException> { service.prefetchNext(SpotDetailPart.BODY, 0L, 10) }
    }

    @Test
    fun `AC-020 - 한도를 만나면 그 묶음은 적재하지 않는다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L), target(2L)))
        every { tourInfoPort.findDetail(any()) } returns detail() andThenThrows UpstreamRateLimitedException()

        assertFailsWith<UpstreamRateLimitedException> { service.prefetchNext(SpotDetailPart.BODY, 0L, 10) }

        verify(exactly = 0) { upsertPort.saveDetailAbsence(any(), any()) }
    }

    @Test
    fun `대상을 다 처리하면 마지막 id가 커서가 된다`() {
        givenTargets(SpotDetailPart.BODY, listOf(target(1L), target(2L)))
        every { tourInfoPort.findDetail(any()) } returns detail()

        val result = service.prefetchNext(SpotDetailPart.BODY, 0L, 10)

        assertEquals(2, result.processed)
        assertEquals(0, result.failed)
        assertEquals(2L, result.lastSpotId)
    }

    @Test
    fun `AC-002 - 갈래마다 자기 upstream만 부른다`() {
        givenTargets(SpotDetailPart.GALLERY, listOf(target(1L)))
        every { tourInfoPort.findGallery(any()) } returns listOf(image())

        service.prefetchNext(SpotDetailPart.GALLERY, 0L, 10)

        verify(exactly = 1) { tourInfoPort.findGallery(any()) }
        verify(exactly = 0) { tourInfoPort.findDetail(any()) }
        verify(exactly = 1) { upsertPort.saveGallery(1L, any()) }
    }

    @Test
    fun `이용안내는 값이 없어도 행을 남긴다`() {
        givenTargets(SpotDetailPart.INTRO, listOf(target(1L)))
        every { tourInfoPort.findIntro(any(), any()) } returns null

        service.prefetchNext(SpotDetailPart.INTRO, 0L, 10)

        // null을 그대로 넘겨야 어댑터가 전 컬럼 null 행을 남긴다.
        verify(exactly = 1) { upsertPort.saveIntro(1L, CATEGORY, null) }
    }

    @Test
    fun `반복정보는 카테고리를 함께 넘긴다`() {
        givenTargets(SpotDetailPart.INFO, listOf(target(1L)))
        every { tourInfoPort.findInfoItems(any(), any()) } returns listOf(infoItem())

        service.prefetchNext(SpotDetailPart.INFO, 0L, 10)

        // 저장 키에 카테고리가 포함된다.
        verify(exactly = 1) { upsertPort.saveInfoItems(1L, CATEGORY, any()) }
    }

    private fun givenTargets(part: SpotDetailPart, targets: List<PrefetchTarget>) {
        every { targetQueryPort.findTargets(part, any(), any(), any()) } returns targets
    }

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
