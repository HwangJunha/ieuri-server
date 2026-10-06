package org.around.ieuri.tourism.application.visit.provided

import org.around.ieuri.tourism.application.visit.provided.dto.RecordVisitsCommand
import org.around.ieuri.tourism.application.visit.provided.dto.RecordVisitsResult
import org.around.ieuri.tourism.application.visit.provided.dto.RemoveVisitCommand
import org.around.ieuri.tourism.application.visit.provided.dto.RemoveVisitResult
import org.around.ieuri.tourism.domain.shared.DomainLocale

/** 전국 지도 방문 기록 쓰기(SPEC-0030 R3·R4). */
interface DistrictVisitCommandUseCase {

    /**
     * 시군구를 방문으로 기록하고 갱신된 칸을 돌려준다(R3).
     * - 이미 기록된 코드는 중복되지 않으며 오류도 아니다.
     */
    fun recordVisits(command: RecordVisitsCommand, locale: DomainLocale): RecordVisitsResult

    /** 칸의 방문을 해제한다(R4). 그 칸에 속한 시군구 기록이 전부 지워진다. */
    fun removeVisit(command: RemoveVisitCommand, locale: DomainLocale): RemoveVisitResult

    /** 탈퇴 정리. 탈퇴 커밋 뒤 리스너가 부르며 새 트랜잭션이다. 지운 건수(로그용). */
    fun eraseMemberContent(memberId: Long): Int
}
