package org.around.ieuri.tourism.application.recommendation

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.around.ieuri.tourism.application.category.CategoryQueryService
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.category.required.AppCategoryQueryPort
import org.around.ieuri.tourism.application.recommendation.required.EmbeddingTargetQueryPort
import org.around.ieuri.tourism.application.recommendation.required.SpotEmbeddingRepository
import org.around.ieuri.tourism.application.recommendation.required.TextEmbeddingPort
import org.around.ieuri.tourism.application.recommendation.required.dto.EmbeddingTarget
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.recommendation.SpotEmbedding
import org.around.ieuri.tourism.domain.recommendation.SpotRanking
import org.around.ieuri.tourism.domain.recommendation.spec.TasteThreshold
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.spec.TaxRefundTitleRule
import kotlin.test.Test
import kotlin.test.assertEquals

/** SPEC-0035 S2 — 임베딩 적재: 대상 → 텍스트 → 제공자(재시도) → 저장. 포트는 전부 스텁이고, DB 쪽 절반은 실제 `SpotEmbeddingStoreService`다. */
class SpotEmbeddingSyncServiceTest {

    private val targetQueryPort = mockk<EmbeddingTargetQueryPort>()
    private val embeddingRepository = mockk<SpotEmbeddingRepository>(relaxed = true)
    private val embeddingPort = mockk<TextEmbeddingPort>()
    private val categoryPort = mockk<AppCategoryQueryPort>()
    private val policy = RecommendationPolicy(
        ranking = SpotRanking(5_000.0, 0.8, TaxRefundTitleRule(emptyList()), tasteWeight = 0.5, categoryRepeatPenalty = 0.1),
        candidateCap = 300,
        tasteThreshold = TasteThreshold(5),
        embedding = EmbeddingSpec(model = "voyage-4", dimension = 3, retryCount = 2, overviewMaxChars = 500),
    )
    private val service = SpotEmbeddingSyncService(SpotEmbeddingStoreService(targetQueryPort, embeddingRepository, CategoryQueryService(categoryPort, mockk()), policy), embeddingPort, policy)

    private val targets = listOf(
        EmbeddingTarget(11L, "hash-11", DomainLocale.KO, "나이키 가산점", "SHOPPING", "서울 금천구", "스포츠 브랜드 매장이다."),
        EmbeddingTarget(12L, "hash-12", DomainLocale.KO, "가산 칼국수", "RESTAURANT", null, null),
    )

    @Test
    fun `대상이 없으면 소진이다`() {
        every { targetQueryPort.findTargets(0L, 100, "voyage-4") } returns emptyList()
        val result = service.syncNext(0L, 100)
        assertEquals(0, result.processed)
        assertEquals(0L, result.lastTranslationId)
    }

    @Test
    fun `행의 언어로 라벨을 붙인 텍스트를 임베딩해 저장하고 커서를 옮긴다`() {
        every { targetQueryPort.findTargets(0L, 100, "voyage-4") } returns targets
        every { categoryPort.findDisplays(setOf("SHOPPING", "RESTAURANT"), DomainLocale.KO) } returns
            mapOf("SHOPPING" to CategoryDisplay(SpotCategory("SHOPPING"), "쇼핑", null))
        val texts = slot<List<String>>()
        every { embeddingPort.embed(capture(texts), "voyage-4") } returns listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))
        val saved = slot<List<SpotEmbedding>>()
        every { embeddingRepository.saveAll(capture(saved)) } returns Unit

        val result = service.syncNext(0L, 100)

        // 라벨이 없는 카테고리는 코드가 라벨이다. 주소가 없으면 빈 칸.
        assertEquals(listOf("나이키 가산점 | 쇼핑 | 서울 금천구 | 스포츠 브랜드 매장이다.", "가산 칼국수 | RESTAURANT |  |"), texts.captured)
        assertEquals(listOf(11L, 12L), saved.captured.map { it.translationId })
        // 읽을 때의 원문 지문을 벡터와 함께 저장한다 — 바뀌면 다음 실행의 대상이 된다.
        assertEquals(listOf("hash-11", "hash-12"), saved.captured.map { it.sourceHash })
        assertEquals(listOf("voyage-4", "voyage-4"), saved.captured.map { it.model })
        assertEquals(listOf(1f, 0f, 0f), saved.captured[0].vector.toList())
        assertEquals(2, result.processed)
        assertEquals(2, result.embedded)
        assertEquals(0, result.failed)
        assertEquals(12L, result.lastTranslationId)
    }

    @Test
    fun `제공자가 실패하면 설정 횟수까지 바로 재시도한다`() {
        every { targetQueryPort.findTargets(any(), any(), any()) } returns targets
        every { categoryPort.findDisplays(any(), any()) } returns emptyMap()
        every { embeddingPort.embed(any(), any()) } throws IllegalStateException("429") andThen listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))

        val result = service.syncNext(0L, 100)

        assertEquals(2, result.embedded)
        verify(exactly = 2) { embeddingPort.embed(any(), any()) }
    }

    @Test
    fun `재시도를 다 써도 실패하면 건너뛰고 실패로 세되 커서는 옮긴다`() {
        every { targetQueryPort.findTargets(any(), any(), any()) } returns targets
        every { categoryPort.findDisplays(any(), any()) } returns emptyMap()
        every { embeddingPort.embed(any(), any()) } throws IllegalStateException("down")

        val result = service.syncNext(0L, 100)

        assertEquals(2, result.processed)
        assertEquals(0, result.embedded)
        assertEquals(2, result.failed)
        assertEquals(12L, result.lastTranslationId)
        verify(exactly = 3) { embeddingPort.embed(any(), any()) }
        verify(exactly = 0) { embeddingRepository.saveAll(any()) }
    }

    @Test
    fun `차원이 설정과 다른 벡터는 저장하지 않고 실패로 센다`() {
        every { targetQueryPort.findTargets(any(), any(), any()) } returns targets
        every { categoryPort.findDisplays(any(), any()) } returns emptyMap()
        every { embeddingPort.embed(any(), any()) } returns listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f))
        val saved = slot<List<SpotEmbedding>>()
        every { embeddingRepository.saveAll(capture(saved)) } returns Unit

        val result = service.syncNext(0L, 100)

        assertEquals(listOf(11L), saved.captured.map { it.translationId })
        assertEquals(1, result.embedded)
        assertEquals(1, result.failed)
    }
}
