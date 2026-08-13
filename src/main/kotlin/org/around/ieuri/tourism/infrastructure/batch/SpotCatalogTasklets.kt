package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.`in`.command.SpotCatalogUseCase
import org.around.ieuri.tourism.application.port.out.query.SpotCatalogQueryPort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import java.nio.file.Path

/**
 * 카탈로그 배정 Step 본체(SPEC-0019 §3).
 *
 * **동기화·필터 Step 뒤에 온다** — 적재되지 않은 행은 배정할 것이 없고, 비관광 spot은
 * 적재 시점에 이미 빠져 있다(SPEC-0018).
 *
 * 한 번 실행에 [SpotCatalogUseCase.assignCatalogs]가 정한 상한만큼 처리하고, 남은 것이
 * 있으면 `CONTINUABLE`로 다시 돈다. 최초 실행은 115,217행 전량이 대상이라 여러 번 나뉜다.
 */
class SpotCatalogAssignmentTasklet(
    private val useCase: SpotCatalogUseCase,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext

        val result = useCase.assignCatalogs()

        val totalAssigned = executionContext.getInt(KEY_ASSIGNED, 0) + result.assigned
        val totalCreated = executionContext.getInt(KEY_CREATED, 0) + result.catalogsCreated
        val totalUnresolved = executionContext.getInt(KEY_UNRESOLVED, 0) + result.unresolved

        executionContext.putInt(KEY_ASSIGNED, totalAssigned)
        executionContext.putInt(KEY_CREATED, totalCreated)
        executionContext.putInt(KEY_UNRESOLVED, totalUnresolved)
        contribution.incrementWriteCount(result.assigned.toLong())

        // 이번 회차에 아무것도 배정하지 못했으면 대상이 소진된 것이다.
        if (result.assigned == 0) {
            log.info(
                "catalog assignment finished: assigned={} created={} unresolved={}",
                totalAssigned, totalCreated, totalUnresolved,
            )
            return RepeatStatus.FINISHED
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_ASSIGNED = "assigned"
        const val KEY_CREATED = "catalogsCreated"
        const val KEY_UNRESOLVED = "unresolved"
    }
}

/**
 * 번역 Step 본체(SPEC-0019 §5).
 *
 * **번역이 비어 있는 (카탈로그, 언어) 쌍만** 대상이며 재번역 경로가 없다(R-7).
 * 초기 34,622건은 시드가 적재하고 이 Step은 이후 신규분만 담당한다.
 */
class SpotCatalogTranslationTasklet(
    private val useCase: SpotCatalogUseCase,
    private val batchSize: Int,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val executionContext = chunkContext.stepContext.stepExecution.executionContext
        val result = useCase.translateMissing(TARGET_LANGUAGES, batchSize)

        val total = executionContext.getInt(KEY_TRANSLATED, 0) + result.translated
        executionContext.putInt(KEY_TRANSLATED, total)
        contribution.incrementWriteCount(result.translated.toLong())

        if (result.translated == 0) {
            log.info("catalog translation finished: translated={}", total)
            return RepeatStatus.FINISHED
        }
        return RepeatStatus.CONTINUABLE
    }

    companion object {
        const val KEY_TRANSLATED = "translated"

        /** 번역은 KO → EN/JA/ZH 단방향이다. KO는 원문이므로 대상이 아니다(R-4). */
        val TARGET_LANGUAGES = setOf(DomainLocale.EN, DomainLocale.JA, DomainLocale.ZH)
    }
}

/**
 * 미해결 CSV Step 본체(SPEC-0019 R-8).
 *
 * **파일 생성 실패가 배치를 실패시키지 않는다** — 카탈로그 배정은 이미 끝난 상태이고 보고는
 * 부수적이다. 여기서 예외를 올리면 성공한 배정까지 실패로 기록된다.
 */
class UnresolvedCatalogReportTasklet(
    private val queryPort: SpotCatalogQueryPort,
    private val outputPath: Path,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        try {
            val reports = queryPort.findUnresolved()
            UnresolvedCatalogCsvWriter.write(reports, outputPath)
            log.info("unresolved catalog report written: count={} path={}", reports.size, outputPath)
            chunkContext.stepContext.stepExecution.executionContext.putInt(KEY_REPORTED, reports.size)
        } catch (e: Exception) {
            // 보고 실패는 로그로만 남긴다. 배정 결과를 되돌릴 이유가 없다.
            log.error("unresolved catalog report failed: path={}", outputPath, e)
        }
        return RepeatStatus.FINISHED
    }

    companion object {
        const val KEY_REPORTED = "reported"
    }
}
