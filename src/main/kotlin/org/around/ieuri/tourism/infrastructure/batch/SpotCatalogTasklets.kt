package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogSplitUseCase
import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 카탈로그 배정 Step 본체(SPEC-0019 §3).
 *
 * **동기화·필터 Step 뒤에 온다** — 적재되지 않은 행은 배정할 것이 없고, 비관광 spot은
 * 적재 시점에 이미 빠져 있다(SPEC-0018).
 *
 * **언어 순서를 지킨다.** KO를 전량 소진한 뒤 EN·JA·ZH로 넘어간다 — KO가 카탈로그를 만들고
 * 나머지 언어가 거기 붙으므로, 순서가 뒤집히면 KO가 있는 장소인데도 외국어가 자기 카탈로그를
 * 만들어 같은 장소가 둘로 갈린다.
 *
 * 한 회차에 정해진 상한만큼 처리하고 남으면 `CONTINUABLE`로 다시 돈다.
 */
class SpotCatalogAssignmentTasklet(
    private val useCase: SpotCatalogUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext

        // 진행 중인 언어를 실행 컨텍스트에 남긴다 — 중단 후 재개해도 같은 지점에서 이어진다.
        val languageIndex = executionContext.getInt(KEY_LANGUAGE_INDEX, 0)
        if (languageIndex >= ASSIGNMENT_ORDER.size) {
            // **배정을 다 마친 뒤에야 고아를 확정할 수 있다** — 앞에 두면 방금 들어온 장소를
            // 잠시 원본 없는 것으로 오인한다.
            useCase.refreshCatalogVisibility()
            log.info(
                "catalog assignment finished: joined={} created={} skipped={}",
                executionContext.getInt(KEY_JOINED, 0),
                executionContext.getInt(KEY_CREATED, 0),
                executionContext.getInt(KEY_SKIPPED, 0),
            )
            return RepeatStatus.FINISHED
        }

        val language = ASSIGNMENT_ORDER[languageIndex]
        // **키셋 커서.** 배정하지 못하고 건너뛴 행은 catalog_id가 null로 남으므로, 커서 없이
        // 재조회하면 같은 200건을 무한히 다시 읽는다.
        val cursor = executionContext.getLong(KEY_CURSOR, 0L)
        val result = useCase.assignCatalogs(language, cursor)

        executionContext.putInt(KEY_JOINED, executionContext.getInt(KEY_JOINED, 0) + result.joined)
        executionContext.putInt(KEY_CREATED, executionContext.getInt(KEY_CREATED, 0) + result.catalogsCreated)
        executionContext.putInt(KEY_SKIPPED, executionContext.getInt(KEY_SKIPPED, 0) + result.skipped)
        executionContext.putLong(KEY_CURSOR, result.lastSpotId)
        contribution.incrementWriteCount(result.assigned.toLong())

        // **`processed`로 판정한다** — `assigned`만 보면 한 회차가 전부 skip일 때 소진으로
        // 오판해 그 언어의 나머지를 통째로 건너뛴다.
        if (result.processed == 0) {
            log.info(
                "catalog assignment: language={} exhausted (skipped={})",
                language, executionContext.getInt(KEY_SKIPPED, 0),
            )
            executionContext.putInt(KEY_LANGUAGE_INDEX, languageIndex + 1)
            // 다음 언어는 처음부터 읽는다.
            executionContext.putLong(KEY_CURSOR, 0L)
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_JOINED = "joined"
        const val KEY_CREATED = "catalogsCreated"
        const val KEY_LANGUAGE_INDEX = "languageIndex"
        const val KEY_SKIPPED = "skipped"

        /** 이 언어에서 마지막으로 읽은 spot id. 언어가 바뀌면 0으로 되돌린다. */
        const val KEY_CURSOR = "cursor"

        /** KO가 먼저다. 나머지 순서는 서로 독립이라 의미가 없다. */
        val ASSIGNMENT_ORDER = listOf(DomainLocale.KO, DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}

/**
 * 잘못 접힌 카탈로그를 가르는 Step 본체(SPEC-0029).
 *
 * **배정 Step 뒤에 온다** — 전량이 붙고 난 뒤라야 "이 카탈로그가 실제로 몇 장소인가"를 물을 수
 * 있다. 배정 중에 물으면 아직 안 붙은 원본을 빼고 판정한다.
 *
 * 회차를 반복하지 않는다. 후보가 한 자릿수(실측 2건)라 한 번에 끝나며, `CONTINUABLE`로 돌면
 * 이미 가른 카탈로그가 다음 회차에 다시 후보로 걸리지 않는다는 보장을 따로 세워야 한다.
 *
 * **가른 뒤 노출 갱신을 다시 부른다.** 배정 Step의 마지막이 이미 한 번 불렀지만, 분할이
 * 번역 행을 옮기고 지웠으므로 그때의 정본은 낡았다 — 다시 부르지 않으면 구독자의 사본이
 * 갈라져 나간 카탈로그를 모른 채 남는다. 가른 것이 없으면 부르지 않는다.
 */
class SpotCatalogSplitTasklet(
    private val splitUseCase: SpotCatalogSplitUseCase,
    private val catalogUseCase: SpotCatalogUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val result = splitUseCase.splitMismatchedCatalogs()
        contribution.incrementWriteCount(result.spotsMoved.toLong())

        if (result.catalogsCreated > 0) catalogUseCase.refreshCatalogVisibility()

        log.info(
            "catalog split finished: split={} created={} moved={}",
            result.catalogsSplit, result.catalogsCreated, result.spotsMoved,
        )
        return RepeatStatus.FINISHED
    }
}
