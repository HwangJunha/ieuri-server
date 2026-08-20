package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.persistence.SpotDistrictAssignmentPort
import org.slf4j.LoggerFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus

/**
 * 카탈로그를 시군구에 귀속시키는 Step 본체(SPEC-0030 R11 · V75).
 *
 * 페이지 루프가 없다 — 귀속 전체가 DB 안의 UPDATE 한 문장이라 한 번 호출하고 끝난다
 * (실측: 전량 43,216행에 1.7초, 평시에는 신규분만이라 훨씬 짧다).
 *
 * **`unassigned`가 0이 되는 것이 목표가 아니다.** 바다·경계 밖 좌표는 어느 시군구에도 속하지
 * 않아 영원히 남는다(실측 273건) — 그 장소가 없는 것이 아니라 시군구에 매칭되지 않을 뿐이며,
 * 칸 조회에서 빠지는 것이 올바른 동작이다(R11). 이 수가 **갑자기 커지면** 경계 데이터나 좌표
 * 적재를 의심한다.
 */
class SpotDistrictAssignmentTasklet(
    private val assignmentPort: SpotDistrictAssignmentPort,
) : Tasklet {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val assigned = assignmentPort.assignUnassignedDistricts()
        val unassigned = assignmentPort.countUnassigned()

        contribution.incrementWriteCount(assigned.toLong())
        log.info("spot district assigned: assigned={} unassigned={}", assigned, unassigned)
        return RepeatStatus.FINISHED
    }
}
