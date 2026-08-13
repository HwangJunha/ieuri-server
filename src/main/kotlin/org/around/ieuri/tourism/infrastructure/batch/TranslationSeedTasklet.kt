package org.around.ieuri.tourism.infrastructure.batch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.ObjectMapper
import org.around.ieuri.tourism.application.port.out.persistence.SpotCatalogSeedPort
import org.around.ieuri.tourism.application.port.out.persistence.SeededTranslation
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.nio.file.Files
import java.nio.file.Path

/**
 * 초기 번역 시드 적재(SPEC-0019 §5.4).
 *
 * 기존 카탈로그 34,622개가 번역을 필요로 하며(결과물 101,201건) **배치가 한 번에 처리하기에
 * 큰 양이고 외부 호출 비용이 든다.** 그래서 초기 데이터는 planner 하네스가 만든 시드로
 * 적재하고, 번역 배치는 이후 신규분만 담당한다.
 *
 * **일회성이다.** 적재가 끝나면 모든 항목이 이미 번역을 가져 매번 0건으로 끝난다.
 */
class TranslationSeedTasklet(
    private val seedPort: SpotCatalogSeedPort,
    private val objectMapper: ObjectMapper,
    private val seedPath: String,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        if (seedPath.isBlank()) {
            log.info("translation seed skipped: no path configured")
            return RepeatStatus.FINISHED
        }

        val path = Path.of(seedPath)
        if (!Files.exists(path)) {
            // 시드 부재는 오류가 아니다 — 이미 적재를 마친 환경에서는 파일을 치워 둔다.
            log.info("translation seed skipped: file not found path={}", path)
            return RepeatStatus.FINISHED
        }

        val entries = Files.newInputStream(path).use {
            objectMapper.readValue(it, Array<TranslationSeedEntry>::class.java)
        }

        var loaded = 0
        var mismatched = 0

        entries.asSequence()
            .chunked(SEED_CHUNK_SIZE)
            .forEach { chunk ->
                val result = seedPort.loadSeed(chunk.mapNotNull { it.toSeededTranslation() })
                loaded += result.loaded
                mismatched += result.mismatched
            }

        log.info("translation seed loaded: entries={} loaded={} mismatched={}", entries.size, loaded, mismatched)
        contribution.incrementWriteCount(loaded.toLong())
        chunkContext.stepContext.stepExecution.executionContext.putInt(KEY_LOADED, loaded)
        return RepeatStatus.FINISHED
    }

    /**
     * 시드 한 항목을 적재 입력으로. `ko_spot_ids`가 비면 붙일 KO 행이 없어 버린다.
     *
     * 번역이 하나도 없는 항목도 버린다 — 쓸 것이 없다.
     */
    private fun TranslationSeedEntry.toSeededTranslation(): SeededTranslation? {
        val spotIds = koSpotIds.mapNotNull(String::toLongOrNull)
        if (spotIds.isEmpty()) return null

        val titles = buildMap {
            en?.takeIf { it.isNotBlank() }?.let { put(DomainLocale.EN, it) }
            ja?.takeIf { it.isNotBlank() }?.let { put(DomainLocale.JA, it) }
            zh?.takeIf { it.isNotBlank() }?.let { put(DomainLocale.ZH, it) }
        }
        if (titles.isEmpty()) return null

        return SeededTranslation(
            koSpotIds = spotIds,
            expectedKoreanTitle = ko,
            expectedMapX = mapX,
            expectedMapY = mapY,
            titles = titles,
        )
    }

    companion object {
        const val KEY_LOADED = "seedLoaded"

        /** 한 번에 적재할 시드 항목 수. 34,622건 전체를 한 트랜잭션에 넣지 않기 위한 분할이다. */
        const val SEED_CHUNK_SIZE = 500
    }
}

/**
 * planner 시드 파일의 한 항목.
 *
 * **`ko_spot_ids`가 실제 `spot.id` 배열이다.** 이 값으로 KO 행을 찾고 그 행의 `catalog_id`에
 * 번역을 붙인다. 배열인 이유는 KO 자체 중복(같은 제목·좌표가 여러 행)이 있어서다 —
 * 어느 행이든 같은 카탈로그에 속하므로 첫 번째를 써도 무방하다.
 *
 * `ko`·`mapX`·`mapY`는 **검증용**이다(§5.4). 찾은 행의 제목·좌표가 시드와 다르면 그 사이
 * TourAPI가 데이터를 바꾼 것이므로 적재하지 않는다.
 *
 * planner의 중간 산출물에 있는 `cid`는 **KO 클러스터 배열의 순번일 뿐 DB의 어떤 컬럼과도
 * 대응하지 않는다.** 이 파일에는 포함되지 않으며 적재에 쓰지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TranslationSeedEntry(
    val ko: String,
    @param:JsonProperty("ko_spot_ids") val koSpotIds: List<String> = emptyList(),
    val mapX: Double,
    val mapY: Double,
    val en: String? = null,
    val ja: String? = null,
    val zh: String? = null,
)
